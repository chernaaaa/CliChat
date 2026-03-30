package ru.hse.clichat.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandLineArgsTest {

    @Test
    void testParseServerMode() {
        String[] args = {"--username", "Alice"};
        CommandLineArgs parsed = CommandLineArgs.parse(args);
        
        assertEquals("Alice", parsed.getUsername());
        assertNull(parsed.getPeerHost());
        assertNull(parsed.getPeerPort());
        assertTrue(parsed.isServer());
    }

    @Test
    void testParseClientMode() {
        String[] args = {"--username", "Bob", "--peer", "localhost", "--port", "8080"};
        CommandLineArgs parsed = CommandLineArgs.parse(args);
        
        assertEquals("Bob", parsed.getUsername());
        assertEquals("localhost", parsed.getPeerHost());
        assertEquals(8080, parsed.getPeerPort());
        assertFalse(parsed.isServer());
    }

    @Test
    void testParseMissingUsernameThrowsException() {
        String[] args = {"--peer", "localhost", "--port", "8080"};
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            CommandLineArgs.parse(args);
        });
        
        assertTrue(exception.getMessage().contains("Имя пользователя"));
    }

    @Test
    void testParseInvalidPortThrowsException() {
        String[] args = {"--username", "Charlie", "--peer", "localhost", "--port", "not_a_number"};
        
        assertThrows(NumberFormatException.class, () -> {
            CommandLineArgs.parse(args);
        });
    }
}
