package com.synauson.jsyn.docs;

// The README's voice-agent stream example is the region between the snippet markers
// below; :jsyn:checkReadmeSnippets fails when the two differ. Nothing runs it.

// snippet: agent-stream
import com.synauson.jsyn.AgentStreamOptions;
import com.synauson.jsyn.EventStreamObserver;
import com.synauson.jsyn.Subscription;
import com.synauson.jsyn.event.AgentEvent;
import com.synauson.jsyn.exception.AgentStreamException;
import com.synauson.jsyn.participant.Conference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

/** Reads a participant's agent stream into a queue, resuming it after a lag. */
public class AgentStreamReader implements EventStreamObserver<AgentEvent> {
    private final Conference conf;
    private final String participantId;
    private final BlockingQueue<AgentEvent> events; // your agent's thread takes from it
    private final Executor executor;                // resubscribes off the engine thread
    private volatile AgentEvent lastStored;         // the resume cursor
    private volatile Subscription subscription;

    public AgentStreamReader(Conference conf, String participantId,
                             BlockingQueue<AgentEvent> events, Executor executor) {
        this.conf = conf;
        this.participantId = participantId;
        this.events = events;
        this.executor = executor;
    }

    public void start() {
        subscription = conf.streamAgentEvents(participantId, AgentStreamOptions.defaults(), this);
    }

    @Override
    public void onNext(AgentEvent event) {
        if (event.isStored()) {
            lastStored = event;
        }
        events.add(event); // quick: never block the engine thread
    }

    @Override
    public void onError(Throwable t) {
        if (!(t instanceof AgentStreamException)) {
            return;
        }
        AgentStreamException e = (AgentStreamException) t;
        AgentEvent last = lastStored;
        boolean lagged = e.reason().equals(AgentStreamException.AGENT_SUBSCRIBER_LAGGED);
        executor.execute(() -> {
            subscription.close();
            try {
                // After a lag, continue exactly after the last event this reader got.
                subscription = conf.streamAgentEvents(participantId, lagged && last != null
                    ? AgentStreamOptions.resumeAfter(last)
                    : AgentStreamOptions.defaults(), this);
            } catch (AgentStreamException expired) {
                // AGENT_REPLAY_EXPIRED: what followed the cursor is gone; start afresh.
                subscription = conf.streamAgentEvents(participantId,
                    AgentStreamOptions.defaults(), this);
            }
        });
    }

    @Override
    public void onCompleted() {
        // StreamEnded came first: the participant or the conference is gone.
    }
}
// end snippet: agent-stream
