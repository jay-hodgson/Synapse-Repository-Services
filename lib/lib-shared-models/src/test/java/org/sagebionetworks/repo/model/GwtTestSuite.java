package org.sagebionetworks.repo.model;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;
import org.sagebionetworks.gwt.client.schema.adapter.GwtAdapterFactory;
import org.sagebionetworks.repo.model.registry.EntityRegistry;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.client.GWTTestCase;


/**
 * Since the GWT test are so slow to start and we could not get the GWTTestSuite to work,
 * we put all GWT tests in one class.
 * @author jmhill
 *
 */
public class GwtTestSuite extends GWTTestCase {
	
	private static final int NUM_PAGINATED_RESULTS = 5;

	/**
	 * Must refer to a valid module that sources this class.
	 */
	public String getModuleName() { 
		return "org.sagebionetworks.repo.SharedSynpaseDTOs";
	}
	
	String registerJson = null;
	
	@Override
	public void gwtSetUp() {

	}
	
	@Override
	public String toString() {
		return "GwtTestSuite for Module: "+getModuleName();
	}
	
	@Test
	public void testAnnotationsRoundTrip() throws JSONObjectAdapterException {
		Annotations annos = new Annotations();
		annos.addAnnotation("string", "one");
		annos.addAnnotation("string", "two");
		annos.addAnnotation("long", new Long(123));
		annos.addAnnotation("double", new Double(123.456));
		annos.addAnnotation("date", new Date());
		
		// Write it to GWT
		GwtAdapterFactory factory = new GwtAdapterFactory();
		JSONObjectAdapter adapter = factory.createNew();
		annos.writeToJSONObject(adapter);
		String json = adapter.toJSONString();
		adapter = factory.createNew(json);
		
		// Clone it
		Annotations clone = new Annotations();
		clone.initializeFromJSONObject(adapter);
		assertEquals(annos, clone);		
	}
	
	@Test
	public void testEntityBundleRoundTrip() throws JSONObjectAdapterException {
		EntityBundle entityBundle = EntityBundleTest.createDummyEntityBundle();
		
		// Write it to GWT
		GwtAdapterFactory factory = new GwtAdapterFactory();
		JSONObjectAdapter adapter = factory.createNew();
		entityBundle.writeToJSONObject(adapter);
		String json = adapter.toJSONString();
		adapter = factory.createNew(json);
		
		// Clone it
		EntityBundle clone = new EntityBundle();
		clone.initializeFromJSONObject(adapter);
		assertEquals(entityBundle, clone);		
	}
	
	@Test
	public void serviceConstantsTest() throws JSONObjectAdapterException, UnsupportedEncodingException{
		assertNotNull(ServiceConstants.DEFAULT_PAGINATION_OFFSET_NO_OFFSET_EQUALS_ONE);
	}
	
}
