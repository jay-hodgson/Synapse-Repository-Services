package org.sagebionetworks.repo.model.dao;

import org.sagebionetworks.evaluation.model.OwnerAttributions;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.web.NotFoundException;

/**
 * The abstraction of the attribution data access object.
 *
 */
public interface AttributionDao {

	/**
	 * Create a new Attribution
	 * @param toCreate
	 * @param ownerId
	 * @param ownerType
	 * @return
	 * @throws NotFoundException
	 */
	public OwnerAttributions create(OwnerAttributions toCreate) throws NotFoundException;
	
	/**
	 * Get the Attribution for an object.
	 * @param ownerId
	 * @param ownerType

	 * @return
	 * @throws NotFoundException 
	 */
	public OwnerAttributions get(String ownerId, String ownerType) throws NotFoundException;
	
	/**
	 * Delete all attributions for an object
	 * 
	 * @param ownerId
	 * @param ownerType
	 */
	public void delete(String ownerId, String ownerType);

	long getCount() throws DatastoreException;

}
