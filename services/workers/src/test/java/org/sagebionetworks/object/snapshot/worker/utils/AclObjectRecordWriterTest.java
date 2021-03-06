package org.sagebionetworks.object.snapshot.worker.utils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sagebionetworks.asynchronous.workers.sqs.MessageUtils;
import org.sagebionetworks.audit.dao.ObjectRecordDAO;
import org.sagebionetworks.audit.utils.ObjectRecordBuilderUtils;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.AccessControlListDAO;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.audit.AclRecord;
import org.sagebionetworks.repo.model.audit.ObjectRecord;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.sagebionetworks.repo.model.message.ChangeType;

import com.amazonaws.services.sqs.model.Message;

public class AclObjectRecordWriterTest {
	
	private AccessControlListDAO mockAccessControlListDao;
	private ObjectRecordDAO mockObjectRecordDao;
	private AclObjectRecordWriter writer;
	private long id = 123L;

	@Before
	public void setup() {
		mockAccessControlListDao = Mockito.mock(AccessControlListDAO.class);
		mockObjectRecordDao = Mockito.mock(ObjectRecordDAO.class);
		writer = new AclObjectRecordWriter(mockAccessControlListDao, mockObjectRecordDao);
	}

	@Test (expected=IllegalArgumentException.class)
	public void deleteAclTest() throws IOException {
		Message message = MessageUtils.buildMessage(ChangeType.DELETE, id+"", ObjectType.ACCESS_CONTROL_LIST, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecord(changeMessage);
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void invalidChangeMessageTest() throws IOException {
		Message message = MessageUtils.buildMessage(ChangeType.UPDATE, id+"", ObjectType.PRINCIPAL, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		writer.buildAndWriteRecord(changeMessage);
	}
	
	@Test
	public void validChangeMessageTest() throws IOException {
		AccessControlList acl = new AccessControlList();
		acl.setEtag("etag");
		Mockito.when(mockAccessControlListDao.get(id)).thenReturn(acl);
		Mockito.when(mockAccessControlListDao.getOwnerType(id)).thenReturn(ObjectType.ENTITY);
		
		Message message = MessageUtils.buildMessage(ChangeType.UPDATE, id+"", ObjectType.ACCESS_CONTROL_LIST, "etag", System.currentTimeMillis());
		ChangeMessage changeMessage = MessageUtils.extractMessageBody(message);
		AclRecord record = AclObjectRecordWriter.buildAclRecord(acl, ObjectType.ENTITY);
		ObjectRecord expected = ObjectRecordBuilderUtils.buildObjectRecord(record, changeMessage.getTimestamp().getTime());
		writer.buildAndWriteRecord(changeMessage);
		Mockito.verify(mockAccessControlListDao).get(id);
		Mockito.verify(mockAccessControlListDao).getOwnerType(id);
		Mockito.verify(mockObjectRecordDao).saveBatch(Mockito.eq(Arrays.asList(expected)), Mockito.eq(expected.getJsonClassName()));
	}

	@Test
	public void buildAclRecordTest() {
		AccessControlList acl = new AccessControlList();
		acl.setCreatedBy("createdBy");
		acl.setCreationDate(new Date(0));
		acl.setEtag("etag");
		acl.setId("id");
		acl.setModifiedBy("modifiedBy");
		acl.setModifiedOn(new Date());
		acl.setResourceAccess(new HashSet<ResourceAccess>());
		acl.setUri("uri");
		AclRecord record = AclObjectRecordWriter.buildAclRecord(acl, ObjectType.EVALUATION);
		assertEquals(ObjectType.EVALUATION, record.getOwnerType());
		assertEquals(acl.getCreatedBy(), record.getCreatedBy());
		assertEquals(acl.getCreationDate(), record.getCreationDate());
		assertEquals(acl.getEtag(), record.getEtag());
		assertEquals(acl.getModifiedBy(), record.getModifiedBy());
		assertEquals(acl.getModifiedOn(), record.getModifiedOn());
		assertEquals(acl.getId(), record.getId());
		assertEquals(acl.getResourceAccess(), record.getResourceAccess());
		assertEquals(acl.getUri(), record.getUri());
	}
}
