package dev.pppp.input;

import dev.pppp.engine.Button;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Turns "a", "a3", "left2down2" (+ optional repeat) into the button steps to press. Throws 400 on anything else. */
public final class CommandParser {
    public static final int MAX_STEPS = 5;
    private static final Pattern TOKEN = Pattern.compile("(a|b|start|select|up|down|left|right|wait)(\\d{1,2})?");
    private static final Pattern COMMAND = Pattern.compile("(?:" + TOKEN.pattern() + ")+");

    private CommandParser() {}

    public static List<Button> parse(String command, int repeat, String mode) {
        String c = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (!COMMAND.matcher(c).matches()) throw bad("Unknown command: " + command);
        if (repeat < 1) throw bad("repeat must be at least 1");

        List<Button> steps = new ArrayList<>();
        int tokens = 0;
        var m = TOKEN.matcher(c);
        while (m.find()) {
            tokens++;
            int n = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            if (n < 1) throw bad("A count must be at least 1");
            // Stop one past the cap: enough to reject, without expanding "a99".
            for (int i = 0; i < n && steps.size() <= MAX_STEPS; i++) {
                steps.add(Button.valueOf(m.group(1).toUpperCase(Locale.ROOT)));
            }
        }
        if (repeat > 1) {
            if (steps.size() > 1) throw bad("Use either a count (a3) or repeat, not both");
            while (steps.size() < repeat && steps.size() <= MAX_STEPS) steps.add(steps.getFirst());
        }
        if (tokens > 1 && "DEMOCRACY".equals(mode)) throw bad("Sequences are not allowed in democracy");
        if (steps.size() > MAX_STEPS) throw bad("At most " + MAX_STEPS + " steps per input");
        return steps;
    }

    /** Stream "cmd" field: comma-separated button names, e.g. "LEFT,LEFT,DOWN,DOWN". */
    public static String serialize(List<Button> steps) {
        return String.join(",", steps.stream().map(Button::name).toList());
    }

    public static List<Button> deserialize(String cmd) {
        return Arrays.stream(cmd.split(",")).map(Button::valueOf).toList();
    }

    private static ResponseStatusException bad(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
