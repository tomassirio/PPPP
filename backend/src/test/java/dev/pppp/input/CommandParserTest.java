package dev.pppp.input;

import static dev.pppp.engine.Button.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.pppp.engine.Button;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

class CommandParserTest {

    @ParameterizedTest
    @CsvSource({
            "a,          1, ANARCHY,   A",
            "A,          1, ANARCHY,   A",
            "' start ',  1, ANARCHY,   START",
            "a3,         1, ANARCHY,   A A A",
            "a,          3, ANARCHY,   A A A",
            "left2down2, 1, ANARCHY,   LEFT LEFT DOWN DOWN",
            "abwait,     1, ANARCHY,   A B WAIT",
            "up5,        1, ANARCHY,   UP UP UP UP UP",
            "b4,         1, DEMOCRACY, B B B B",
            "b,          4, DEMOCRACY, B B B B",
    })
    void parses(String command, int repeat, String mode, String expected) {
        List<Button> want = java.util.Arrays.stream(expected.split(" ")).map(Button::valueOf).toList();
        assertEquals(want, CommandParser.parse(command, repeat, mode));
    }

    @ParameterizedTest
    @CsvSource({
            "x,          1, ANARCHY",
            "'',         1, ANARCHY",
            "a b,        1, ANARCHY",
            "a0,         1, ANARCHY",
            "a6,         1, ANARCHY",
            "a99,        1, ANARCHY",
            "a,          6, ANARCHY",
            "a,          0, ANARCHY",
            "a3b3,       1, ANARCHY",
            "a2,         2, ANARCHY",
            "ab,         2, ANARCHY",
            "left2down2, 1, DEMOCRACY",
    })
    void rejects(String command, int repeat, String mode) {
        var e = assertThrows(ResponseStatusException.class, () -> CommandParser.parse(command, repeat, mode));
        assertEquals(400, e.getStatusCode().value());
    }

    @org.junit.jupiter.api.Test
    void serializeRoundTrips() {
        var steps = List.of(LEFT, LEFT, DOWN);
        assertEquals("LEFT,LEFT,DOWN", CommandParser.serialize(steps));
        assertEquals(steps, CommandParser.deserialize("LEFT,LEFT,DOWN"));
    }
}
