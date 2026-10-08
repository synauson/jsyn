package com.synauson.jsyn.docs;

// The README's turn example is the region between the snippet markers below;
// :jsyn:checkReadmeSnippets fails when the two differ. Nothing runs it.

// snippet: agent-turns
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.TurnDetectionConfig;
import com.synauson.jsyn.spec.TurnConfigUpdate;
import java.util.concurrent.BlockingQueue;

/** Answers each of a caller's turns, from events an AgentStreamReader queued. */
public class AgentTurns {
    /** Turn detection for the caller, ending a turn after 3 s of silence at the latest. */
    public static TurnDetectionConfig turnDetection() {
        return TurnDetectionConfig.defaults()
            .withTurns(TurnConfigUpdate.none().withEndOfTurnTimeoutMs(3_000));
    }

    public static void run(Conference conf, String callerId, BlockingQueue<AgentEvent> events)
            throws InterruptedException {
        while (true) {
            AgentEvent event = events.take();
            if (event instanceof AgentEvent.EndOfTurn) {
                AgentEvent.EndOfTurn end = (AgentEvent.EndOfTurn) event;
                // end.reason says why: MODEL, TIMEOUT, MANUAL or STREAM_ENDED.
                answer(end.text);
            } else if (event instanceof AgentEvent.StreamEnded) {
                return;
            }
        }
    }

    /** The caller pressed a key that means "done": end the turn now. */
    public static void doneKey(Conference conf, String callerId) {
        conf.forceEndTurn(callerId); // its EndOfTurn (MANUAL) follows on the stream
    }

    /** Give the caller time to read out a long number. */
    public static void morePatience(Conference conf, String callerId) {
        conf.updateTurnConfig(callerId, TurnConfigUpdate.none().withEndOfTurnTimeoutMs(10_000));
    }

    private static void answer(String text) {
        // Your LLM and TTS go here.
    }
}
// end snippet: agent-turns
