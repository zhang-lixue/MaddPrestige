package net.maddkraft.maddprestige.persistence;

public interface TransactionUnit {
    <T> T execute(TransactionalWork<T> work);

    @FunctionalInterface
    interface TransactionalWork<T> {
        T run() throws Exception;
    }
}
