package dev.pppp;

import static org.mockito.Mockito.withSettings;

import dev.pppp.emulator.Button;
import dev.pppp.emulator.EmulatorHost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * A mocked EmulatorHost whose answers come from fields. Tests set the fields instead of stubbing with
 * when()/doAnswer(): the app's scheduler threads call the host all the time, and Mockito stubbing is not
 * thread-safe (a call from another thread mid-stubbing gives UnfinishedStubbing or stubs the wrong method).
 * verify() still works on the mock.
 *
 * <pre>
 * static final HostStub STUB = new HostStub();
 * &#64;TestBean EmulatorHost host;
 * static EmulatorHost host() { return STUB.mock(); }
 * </pre>
 */
public class HostStub implements Answer<Object> {
    public volatile EmulatorHost.Status status = new EmulatorHost.Status(EmulatorHost.Power.OFF, null, false, 1.0);
    public volatile byte[] saveState;
    public final List<Button> presses = Collections.synchronizedList(new ArrayList<>());

    public EmulatorHost mock() {
        return org.mockito.Mockito.mock(EmulatorHost.class, withSettings().defaultAnswer(this));
    }

    @Override
    public Object answer(InvocationOnMock inv) throws Throwable {
        return switch (inv.getMethod().getName()) {
            case "status" -> status;
            case "saveState" -> saveState;
            case "press" -> {
                presses.add(inv.getArgument(0));
                yield null;
            }
            default -> Answers.RETURNS_DEFAULTS.answer(inv);
        };
    }
}
