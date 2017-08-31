package helpers.accesslog;

import play.libs.F;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

public class CappedEventStream<T> {
    final int bufferSize;
    final ConcurrentLinkedQueue<T> events = new ConcurrentLinkedQueue();
    final List<F.Promise<T>> waiting = Collections.synchronizedList(new ArrayList());

    public CappedEventStream() {
        this.bufferSize = 100;
    }

    public CappedEventStream(int maxBufferSize) {
        this.bufferSize = maxBufferSize;
    }

    public synchronized F.Promise<T> nextEvent() {
        if (this.events.isEmpty()) {
            CappedEventStream<T>.LazyTask task = new CappedEventStream.LazyTask();
            this.waiting.add(task);
            return task;
        } else {
            return new CappedEventStream.LazyTask(this.events.peek());
        }
    }

    public synchronized void publish(T event) {
        if (this.events.size() > this.bufferSize) {
            this.events.poll();
        }

        this.events.offer(event);
        this.notifyNewEvent();
    }

    void notifyNewEvent() {
        T value = this.events.peek();
        Iterator var2 = this.waiting.iterator();

        while(var2.hasNext()) {
            F.Promise<T> task = (F.Promise)var2.next();
            task.invoke(value);
        }

        this.waiting.clear();
    }

    class LazyTask extends F.Promise<T> {
        public LazyTask() {
        }

        public LazyTask(T value) {
            this.invoke(value);
        }

        public T get() throws InterruptedException, ExecutionException {
            T value = super.get();
            this.markAsRead(value);
            return value;
        }

        public T getOrNull() {
            T value = super.getOrNull();
            this.markAsRead(value);
            return value;
        }

        private void markAsRead(T value) {
            if (value != null) {
                CappedEventStream.this.events.remove(value);
            }

        }
    }
}

