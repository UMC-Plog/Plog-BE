package com.plog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlogApplicationTests {

    @Test
    void applicationClassLoads() {
        assertDoesNotThrow(() ->
                Class.forName("com.plog.PlogApplication")
        );
    }
}
