package com.synauson.jsyn.docs;

// The README's speech example is the region between the snippet markers below;
// :jsyn:checkReadmeSnippets fails when the two differ. Nothing runs it.

// snippet: agent-speak
import com.synauson.jsyn.CancelledUtterance;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.participant.Conference;
import com.synauson.jsyn.spec.Speak;
import com.synauson.jsyn.spec.TtsConfig;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;

/** Speaks an answer to each of the caller's turns and stops when the caller talks over it. */
public class AgentSpeech {
    /** The caller's speaker, set on its spec next to VadConfig and TurnDetectionConfig. */
    public static TtsConfig voice() {
        return TtsConfig.defaults().withVoice("en-us-m1");
    }

    private int replies;

    public void run(Conference conf, String callerId, BlockingQueue<AgentEvent> events)
            throws InterruptedException {
        while (true) {
            AgentEvent event = events.take();
            if (event instanceof AgentEvent.EndOfTurn) {
                String id = "reply-" + (++replies);
                speak(conf, callerId, id, answer(((AgentEvent.EndOfTurn) event).text));
            } else if (event instanceof AgentEvent.SpeechStarted) {
                // The caller talks over the agent: stop, and keep only what they heard.
                for (CancelledUtterance c : conf.cancelUtterances(callerId)) {
                    heard(c.utteranceId, c.heardText);
                }
            } else if (event instanceof AgentEvent.StreamEnded) {
                return;
            }
        }
    }

    /**
     * Stream an LLM's reply as it is written; speech starts after the first words. A real
     * agent runs this off the event loop, so it still sees the caller barge in.
     */
    private static void speak(Conference conf, String callerId, String id, Iterator<String> tokens) {
        while (tokens.hasNext()) {
            conf.speak(callerId, Speak.builder().utteranceId(id).text(tokens.next()).build());
        }
        conf.speak(callerId, Speak.builder().utteranceId(id).release(Speak.Release.END).build());
    }

    private static Iterator<String> answer(String callerSaid) {
        throw new UnsupportedOperationException("your LLM goes here");
    }

    private static void heard(String utteranceId, String heardText) {
        // Record in the conversation what the caller actually heard.
    }
}
// end snippet: agent-speak
