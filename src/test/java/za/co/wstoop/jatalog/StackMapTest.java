package za.co.wstoop.jatalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.engine.StackMap;

/*
 * I'm not too concerned to get 100% coverage for StackMap because it is
 * basically just the get(), put() and containsKey() methods that are used
 * by Jatalog
 */
public class StackMapTest {

	@Test
	public void testBase() throws DatalogException {
		Map<String, String> parent = new StackMap<>();
		StackMap<String, String> child = new StackMap<>(parent);
		
		assertTrue(child.isEmpty());
		
		parent.put("W", "0");
		parent.put("X", "1");
		assertFalse(child.isEmpty());
				
		child.put("Y", "2");
		assertTrue(child.get("X").equals("1"));
		assertTrue(child.get("Y").equals("2"));		
		assertTrue(child.get("Z") == null);

		assertTrue(child.containsKey("X"));
		assertTrue(child.containsKey("Y"));
		assertFalse(child.containsKey("Z"));

		assertTrue(child.containsValue("1"));
		assertTrue(child.containsValue("2"));
		assertFalse(child.containsValue("3"));
		
		child.put("X", "5");
		assertTrue(child.get("X").equals("5"));
		assertTrue(parent.get("X").equals("1"));
		assertTrue(child.containsValue("5"));
		assertFalse(parent.containsValue("5"));
		
		assertTrue(child.size() == 3);

		assertTrue(child.toString().contains("X: 5"));
		assertTrue(child.toString().contains("Y: 2"));
		assertTrue(child.toString().contains("W: 0"));
		
		Map<String, String> flat = child.flatten();
		assertTrue(flat.get("W").equals("0"));
		assertTrue(flat.get("X").equals("5"));
		assertTrue(flat.get("Y").equals("2"));	
		assertTrue(flat.get("Z") == null);	
		
		child.clear();
		assertTrue(parent.get("X").equals("1"));
		assertTrue(child.size() == 0);
		assertTrue(child.get("X") == null);	
	}
	
}
