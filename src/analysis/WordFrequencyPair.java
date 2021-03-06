package analysis;

import org.deeplearning4j.berkeley.Pair;

/**
 * Created by ehallmark on 9/5/16.
 */
public class WordFrequencyPair<T,V extends Comparable<V>> implements Comparable< WordFrequencyPair<T,V>> {
    private T first;
    private V second;
    public WordFrequencyPair(T first, V second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }

    public void setFirst(T first) {
        this.first=first;
    }

    public void setSecond(V second) {
        this.second=second;
    }

    @Override
    public int compareTo(WordFrequencyPair<T,V> other) {
        return getSecond().compareTo(other.getSecond());
    }
}
