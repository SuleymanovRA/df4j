/**
 * This package contain interfaces to support unbounded stream of messages.
 * Each stream contains any number of calls to {@link org.df4j.core.boundconnector.messagestream.StreamCollector#post(java.lang.Object)}
 * and finishes with either call to {@link org.df4j.core.boundconnector.messagestream.StreamCollector#completeExceptionally(java.lang.Throwable)} }
 * or {@link org.df4j.core.boundconnector.messagestream.StreamCollector#complete()} }
 */
package org.df4j.core.boundconnector.messagestream;

