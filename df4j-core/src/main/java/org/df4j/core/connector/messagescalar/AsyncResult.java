package org.df4j.core.connector.messagescalar;

import java.util.concurrent.Future;

public interface AsyncResult<R> extends ScalarPublisher<R>, Future<R> {
}
