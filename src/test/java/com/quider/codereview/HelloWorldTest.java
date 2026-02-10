package com.quider.codereview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for HelloWorld.
 */
public class HelloWorldTest {
    
    @Test
    public void testGetMessage() {
        String message = HelloWorld.getMessage();
        assertEquals("Hello, World!", message);
    }
    
    @Test
    public void testGetMessageNotNull() {
        String message = HelloWorld.getMessage();
        assertNotNull(message);
    }
    
    @Test
    public void testGetMessageNotEmpty() {
        String message = HelloWorld.getMessage();
        assertFalse(message.isEmpty());
    }
}
