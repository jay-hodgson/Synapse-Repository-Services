package org.sagebionetworks.repo.model.jdo;

import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_ACL_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_ACL_OWNER_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_ACL_OWNER_TYPE;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_RESOURCE_ACCESS_GROUP_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_RESOURCE_ACCESS_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_RESOURCE_ACCESS_OWNER;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_RESOURCE_ACCESS_TYPE_ELEMENT;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.COL_RESOURCE_ACCESS_TYPE_ID;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.TABLE_ACCESS_CONTROL_LIST;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.TABLE_RESOURCE_ACCESS;
import static org.sagebionetworks.repo.model.query.jdo.SqlConstants.TABLE_RESOURCE_ACCESS_TYPE;

public class AuthorizationSqlUtil {
	
	/*
	 * select acl.id from jdoaccesscontrollist acl, jdoresourceaccess ra, access_type at
	 * where
	 * ra.oid_id=acl.id and ra.groupId in :groups and at.oid_id=ra.id and at.type=:type
	 */

	private static final String AUTHORIZATION_SQL_SELECT = "select acl." + COL_ACL_OWNER_ID + " " + COL_ACL_ID;

	public static final String AUTHORIZATION_SQL_TABLES =
			TABLE_ACCESS_CONTROL_LIST+" acl, "+
			TABLE_RESOURCE_ACCESS+" ra, "+
			TABLE_RESOURCE_ACCESS_TYPE+" at ";
	
	public static final String AUTHORIZATION_SQL_JOIN =
		    " acl."+COL_ACL_ID+"=ra."+COL_RESOURCE_ACCESS_OWNER+
		    " and at."+COL_RESOURCE_ACCESS_TYPE_ID+"=ra."+COL_RESOURCE_ACCESS_ID;
			
	
	private static final String AUTHORIZATION_SQL_WHERE_1 = 
		"where (ra."+COL_RESOURCE_ACCESS_GROUP_ID+
		" in (";

	/**
	 * The bind variable used to set the access type for authorization filters.
	 */
	public static final String ACCESS_TYPE_BIND_VAR = "type";
	public static final String RESOURCE_ID_BIND_VAR = "resourceId";
	public static final String PRINCIPAL_IDS_BIND_VAR = "principalIds";
	public static final String RESOURCE_TYPE_BIND_VAR = COL_ACL_OWNER_TYPE;
	
	private static final String AUTHORIZATION_SQL_WHERE_2 = 
		")) AND "+AUTHORIZATION_SQL_JOIN+
	    " and acl."+COL_ACL_OWNER_TYPE+"=:"+RESOURCE_TYPE_BIND_VAR+
		" and at."+COL_RESOURCE_ACCESS_TYPE_ELEMENT+"=:"+ACCESS_TYPE_BIND_VAR;
	
	private static final String CAN_ACCESS_SQL_1 =
			"SELECT COUNT(acl." + COL_ACL_ID + ") " +
			"FROM " +AUTHORIZATION_SQL_TABLES +
			"WHERE " +
			AUTHORIZATION_SQL_JOIN +
			" AND (ra."+COL_RESOURCE_ACCESS_GROUP_ID+" IN (";

	private static final String CAN_ACCESS_SQL_2 = "))" +
					" AND at." + COL_RESOURCE_ACCESS_TYPE_ELEMENT + "=:" + ACCESS_TYPE_BIND_VAR +
					" AND acl." + COL_ACL_OWNER_ID + " =:" + RESOURCE_ID_BIND_VAR
					+ " AND acl."+COL_ACL_OWNER_TYPE+"=:"+RESOURCE_TYPE_BIND_VAR;
	
	public static final String SELECT_RESOURCE_INTERSECTION = "SELECT acl."
			+ COL_ACL_OWNER_ID
			+ " as "+COL_ACL_OWNER_ID+" FROM "
			+ AUTHORIZATION_SQL_TABLES
			+ " WHERE "
			+ AUTHORIZATION_SQL_JOIN
			+ " AND ra."
			+ COL_RESOURCE_ACCESS_GROUP_ID
			+ " IN (:"
			+ PRINCIPAL_IDS_BIND_VAR
			+ ") AND at."
			+ COL_RESOURCE_ACCESS_TYPE_ELEMENT
			+ "=:"
			+ ACCESS_TYPE_BIND_VAR
			+ " AND acl."
			+ COL_ACL_OWNER_ID
			+ " IN (:"
			+ RESOURCE_ID_BIND_VAR
			+ ") AND acl." + COL_ACL_OWNER_TYPE + "=:" + RESOURCE_TYPE_BIND_VAR;

	/**
	 * The bind variable prefix used for group ID for the authorization SQL.
	 */
	public static final String BIND_VAR_PREFIX = "g";

	/**
	 * This returns a 'select' statement suitable for using as a subquery
	 * when selecting objects matching other criteria
	 * @param n number of principals (user groups) in the parameter set
	 * @return the SQL to find the root-accessible nodes that a specified user-group list can access
	 * using a specified access type
	 */
	public static String authorizationSQL(int n, int offset) {
		StringBuilder sb = new StringBuilder(AUTHORIZATION_SQL_SELECT);
		sb.append(" FROM ");
		sb.append(AUTHORIZATION_SQL_TABLES);
		sb.append(authorizationSQLWhere(n, offset));
		return sb.toString();
	}
	
	/**
	 * 
	 * @param n number of principals (user groups) in the parameter set
	 * @return the 'where' clause for the authorization SQL
	 * 
	 * Can't bind a collection to a variable in the string, so we have to create n bind variables 
	 * for a collection of length n.  :^(
	 */
	public static String authorizationSQLWhere(int n, int offset) {
		StringBuilder sb = new StringBuilder(AUTHORIZATION_SQL_WHERE_1);
		for (int i=0; i<n; i++) {
			if (i>0) sb.append(",");
			sb.append(":");
			sb.append(BIND_VAR_PREFIX);
			sb.append(i + offset);
		}
		sb.append(AUTHORIZATION_SQL_WHERE_2);
		return sb.toString();
	}

	/**
	 * Create the canAccess Sql
	 * @param numberUserGroups
	 * @return
	 */
	public static String authorizationCanAccessSQL(int numberUserGroups){
		StringBuilder sb = new StringBuilder(CAN_ACCESS_SQL_1);
		for (int i=0; i<numberUserGroups; i++) {
			if (i>0) sb.append(",");
			sb.append(":");
			sb.append(BIND_VAR_PREFIX);
			sb.append(i);
		}
		sb.append(CAN_ACCESS_SQL_2);
		return sb.toString();
	}
}
