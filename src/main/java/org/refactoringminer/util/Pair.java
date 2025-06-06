package org.refactoringminer.util;

public class Pair<T, U> {
    T gt;
    U pred;
    double similarity;

    public Pair(T gt, U pred, double similarity) {
        this.gt = gt;
        this.pred = pred;
        this.similarity = similarity;
    }
}
