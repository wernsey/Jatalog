package za.co.wstoop.jatalog;


import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Basic unit tests.
 */
public class JatalogTest {
	
	@Test
	public void testEquals() {
		/* Truth be told the only reason I need Jatalog.equals() to work
		 * is so that I can test Jatalog.toString() */
		try {
			Jatalog thisJatalog = TestUtils.createDatabase();
			Jatalog thatJatalog = TestUtils.createDatabase();
		
			assertTrue(thisJatalog != thatJatalog);
			assertTrue(thisJatalog.equals(thatJatalog));
			
			thatJatalog.fact("foo", "bar");
			assertFalse(thisJatalog.equals(thatJatalog));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testToString() {
		try {
			Jatalog thisJatalog = TestUtils.createDatabase();			
			String string = thisJatalog.toString();
			
			Jatalog thatJatalog = new Jatalog();
			thatJatalog.executeAll(string);
						
			assertTrue(thisJatalog != thatJatalog);
			assertTrue(thisJatalog.equals(thatJatalog));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}
}
