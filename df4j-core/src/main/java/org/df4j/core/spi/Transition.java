package org.df4j.core.spi;

import java.util.Iterator;

public interface Transition extends Iterable<Registration> {
    Registration register(Connector connector);

    void postActivity(Activity activity);
}
