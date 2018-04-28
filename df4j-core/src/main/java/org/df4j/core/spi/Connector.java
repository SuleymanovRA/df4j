package org.df4j.core.spi;

public interface Connector {
    /**
     * Executed after token processing (method act). Cleans reference to
     * activiactivityPlace, if any. Signals to set state to off if no more tokens are in
     * the place. Should return quickly, as is called from the actor's
     * synchronized block.
     */
    void purge();
}
