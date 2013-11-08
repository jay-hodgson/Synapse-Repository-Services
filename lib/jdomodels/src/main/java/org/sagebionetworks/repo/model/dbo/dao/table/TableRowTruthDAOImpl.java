package org.sagebionetworks.repo.model.dbo.dao.table;

import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_ID_SEQUENCE_TABLE_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_TABLE_ROW_KEY;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.TABLE_ROW_CHANGE;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.sagebionetworks.repo.model.dao.table.TableRowTruthDAO;
import org.sagebionetworks.repo.model.dbo.DBOBasicDao;
import org.sagebionetworks.repo.model.dbo.persistence.table.DBOTableIdSequence;
import org.sagebionetworks.repo.model.dbo.persistence.table.DBOTableRowChange;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.IdRange;
import org.sagebionetworks.repo.model.table.Row;
import org.sagebionetworks.repo.model.table.RowReference;
import org.sagebionetworks.repo.model.table.RowReferenceSet;
import org.sagebionetworks.repo.model.table.RowSet;
import org.sagebionetworks.repo.model.table.TableRowChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sns.model.NotFoundException;

/**
 * Basic S3 & RDS implementation of the TableRowTruthDAO.
 * @author John
 *
 */
public class TableRowTruthDAOImpl implements TableRowTruthDAO {

	private static final String SQL_SELECT_ROW_CHANGE_FOR_TABLE_AND_VERSION = "SELECT * FROM "+TABLE_ROW_CHANGE+" WHERE "+COL_TABLE_ROW_TABLE_ID+" = ? AND "+COL_TABLE_ROW_VERSION+" = ?";
	private static final String SQL_LIST_ALL_KEYS = "SELECT "+COL_TABLE_ROW_KEY+" FROM "+TABLE_ROW_CHANGE;
	private static final String SQL_SELECT_ALL_ROW_CHANGES_FOR_TABLE = "SELECT * FROM "+TABLE_ROW_CHANGE+" WHERE "+COL_TABLE_ROW_TABLE_ID+" = ? ORDER BY "+COL_TABLE_ROW_VERSION+" ASC";
	private static final String KEY_TEMPLATE = "%1$s.csv.gz";
	private static final String SQL_TRUNCATE_SEQUENCE_TABLE = "DELETE FROM "+TABLE_TABLE_ID_SEQUENCE+" WHERE "+COL_ID_SEQUENCE_TABLE_ID+" > 0";
	private static final String SQL_SELECT_SEQUENCE_FOR_UPDATE = "SELECT * FROM "+TABLE_TABLE_ID_SEQUENCE+" WHERE "+COL_ID_SEQUENCE_TABLE_ID+" = ? FOR UPDATE";
	@Autowired
	private DBOBasicDao basicDao;
	@Autowired
	private SimpleJdbcTemplate simpleJdbcTemplate;
	@Autowired
	private AmazonS3Client s3Client;
	
	private String s3Bucket;
	
	public String getS3Bucket() {
		return s3Bucket;
	}

	/**
	 * IoC
	 * @param s3Bucket
	 */
	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	RowMapper<DBOTableIdSequence> sequenceRowMapper = new DBOTableIdSequence().getTableMapping();
	RowMapper<DBOTableRowChange> rowChangeMapper = new DBOTableRowChange().getTableMapping();
	
	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	@Override
	public IdRange reserveIdsInRange(String tableIdString, long countToReserver) {
		if(tableIdString == null) throw new IllegalArgumentException("TableId cannot be null");
		long tableId = KeyFactory.stringToKey(tableIdString);

		// Setup the dbo
		DBOTableIdSequence dbo = null;
		boolean exists = false;
		// If this table already exists, lock the row and get the current value.
		Long currentSequence;
		Long currentVersion;
		try {
			// First lock row for this table
			dbo = simpleJdbcTemplate.queryForObject(SQL_SELECT_SEQUENCE_FOR_UPDATE, sequenceRowMapper, tableId);
			currentSequence = dbo.getSequence();
			currentVersion = dbo.getVersionNumber();
			exists = true;
		} catch (EmptyResultDataAccessException e) {
			// This table does not exist yet
			currentSequence = -1l;
			currentVersion = -1l;
			exists = false;
		}
		// Create the new values
		dbo = new DBOTableIdSequence();
		dbo.setSequence(currentSequence+countToReserver);
		dbo.setTableId(tableId);
		dbo.setVersionNumber(currentVersion+1);
		// create or update
		if(exists){
			// update
			basicDao.update(dbo);
		}else{
			// create
			basicDao.createNew(dbo);
		}
		// Prepare the results
		IdRange range = new IdRange();
		if(countToReserver > 0){
			range.setMaximumId(dbo.getSequence());
			range.setMinimumId(dbo.getSequence()-countToReserver+1);
		}
		range.setVersionNumber(dbo.getVersionNumber());
		return range;
	}

	/**
	 * Called after bean creation.
	 */
	public void initialize(){
		// Create the bucket as needed
		s3Client.createBucket(s3Bucket);
	}


	@Transactional(readOnly = false, propagation = Propagation.REQUIRED)
	@Override
	public RowReferenceSet appendRowSetToTable(String userId, String tableId, List<ColumnModel> models, RowSet delta) throws IOException {
		// Now set the row version numbers and ID.
		int coutToReserver = TableModelUtils.countEmptyOrInvalidRowIds(delta);
		// Reserver IDs for the missing
		IdRange range = reserveIdsInRange(tableId, coutToReserver);
		// Now assign the rowIds and set the version number
		TableModelUtils.assignRowIdsAndVersionNumbers(delta, range);
		// We are ready to convert the file to a CSV and save it to S3.
		String key = saveCSVToS3(models, delta);
		List<String> headers = TableModelUtils.getHeaders(models);
		// record the change
		DBOTableRowChange changeDBO = new DBOTableRowChange();
		changeDBO.setTableId(KeyFactory.stringToKey(tableId));
		changeDBO.setRowVersion(range.getVersionNumber());
		changeDBO.setColumnIds(TableModelUtils.createDelimitedColumnModelIdString(headers));
		changeDBO.setCreatedBy(Long.parseLong(userId));
		changeDBO.setCreatedOn(System.currentTimeMillis());
		changeDBO.setKey(key);
		changeDBO.setBucket(s3Bucket);
		basicDao.createNew(changeDBO);
		
		// Prepare the results
		RowReferenceSet results = new RowReferenceSet();
		results.setHeaders(headers);
		results.setTableId(tableId);
		List<RowReference> refs = new LinkedList<RowReference>();
		// Build up the row references
		for(Row row: delta.getRows()){
			RowReference ref = new RowReference();
			ref.setRowId(row.getRowId());
			ref.setVersionNumber(row.getVersionNumber());
			refs.add(ref);
		}
		results.setRows(refs);
		return results;
	}

	/**
	 * Save a change to S3
	 * @param models
	 * @param delta
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private String saveCSVToS3(List<ColumnModel> models, RowSet delta)
			throws IOException, FileNotFoundException {
		File temp = File.createTempFile("rowSet", "csv.gz");
		FileOutputStream out = null;
		try{
			out = new FileOutputStream(temp);
			// Save this to the the zipped CSV
			TableModelUtils.validateAnWriteToCSVgz(models, delta, out);
			// upload it to S3.
			String key = String.format(KEY_TEMPLATE, UUID.randomUUID().toString());
			s3Client.putObject(s3Bucket, key, temp);
			return key;
		}finally{
			if(out != null){
				out.close();
			}
			if(temp != null){
				temp.delete();
			}
		}
	}
	

	/**
	 * List all changes for this table.
	 */
	@Override
	public List<TableRowChange> listRowSetsKeysForTable(String tableIdString) {
		if(tableIdString == null) throw new IllegalArgumentException("TableId cannot be null");
		long tableId = KeyFactory.stringToKey(tableIdString);
		List<DBOTableRowChange> dboList =  simpleJdbcTemplate.query(SQL_SELECT_ALL_ROW_CHANGES_FOR_TABLE, rowChangeMapper, tableId);
		return TableModelUtils.ceateDTOFromDBO(dboList);
	}
	
	/**
	 * Read the RowSet from S3.
	 */
	@Override
	public RowSet getRowSet(String tableIdString, long rowVersion) throws IOException {
		long tableId = KeyFactory.stringToKey(tableIdString);
		try {
			DBOTableRowChange dbo = simpleJdbcTemplate.queryForObject(SQL_SELECT_ROW_CHANGE_FOR_TABLE_AND_VERSION, rowChangeMapper, tableId, rowVersion);
			List<String> headers = TableModelUtils.readColumnModelIdsFromDelimitedString(dbo.getColumnIds());
			// Downlaod the file from S3
			S3Object object = s3Client.getObject(dbo.getBucket(), dbo.getKey());
			try{
				return TableModelUtils.readFromCSVgzStream(object.getObjectContent(), tableIdString, headers);
			}finally{
				// Need to close the stream unconditionally.
				object.getObjectContent().close();
			}
		} catch (EmptyResultDataAccessException e) {
			throw new NotFoundException("RowSet does not exist for tableId: "+tableId+" and row version: "+rowVersion);
		}
	}
	
	@Override
	public void truncateAllRowData() {
		// List key so we can delete them
		List<String> keysToDelete = listAllKeys();
		// Delete each object from S3
		for(String key: keysToDelete){
			s3Client.deleteObject(s3Bucket, key);
		}
		simpleJdbcTemplate.update(SQL_TRUNCATE_SEQUENCE_TABLE);
	}

	/**
	 * List all of the S3 Keys
	 * @return
	 */
	private List<String> listAllKeys() {
		return simpleJdbcTemplate.query(SQL_LIST_ALL_KEYS, new RowMapper<String>() {
			@Override
			public String mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getString(COL_TABLE_ROW_KEY);
			}
		} );
	}

}