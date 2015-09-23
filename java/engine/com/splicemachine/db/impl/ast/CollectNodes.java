package com.splicemachine.db.impl.ast;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.compile.Visitable;
import com.splicemachine.db.iapi.sql.compile.Visitor;
import com.splicemachine.db.impl.sql.compile.QueryTreeNode;

import java.util.LinkedList;
import java.util.List;

/**
 * Collect all nodes designated by predicate. (After Derby's CollectNodesVisitor.)
 *
 * @author P Trolard
 *         Date: 31/10/2013
 */
public class CollectNodes<T> implements Visitor {

    private final List<T> nodeList;
    private final Predicate<? super Visitable> pred;

    public CollectNodes(Predicate<? super Visitable> pred) {
        this.pred = pred;
        this.nodeList = new LinkedList<>();
    }

    @Override
    public boolean visitChildrenFirst(Visitable node) {
        return false;
    }

    @Override
    public boolean stopTraversal() {
        return false;
    }

    @Override
    public Visitable visit(Visitable node, QueryTreeNode parent) {
        if (pred.apply(node)) {
            nodeList.add((T) node);
        }
        return node;
    }

    @Override
    public boolean skipChildren(Visitable node) {
        return false;
    }

    public List<T> getCollected() {
        return nodeList;
    }


    // Builder-like constructor which allows fluent wrapping of CollectNodes visitors
    // with various modifying visitors

    public static <T> CollectNodesBuilder<T> collector(Class<T> clazz){
        return CollectNodes.collector(Predicates.instanceOf(clazz));
    }

    public static <T> CollectNodesBuilder<T> collector(Predicate<? super Visitable> pred){
        return new CollectNodesBuilder<T>(pred);
    }


    public static class CollectNodesBuilder<T> {
        private final CollectNodes<T> collector;
        private Visitor wrapped;

        public CollectNodesBuilder(Predicate<? super Visitable> pred){
            collector = new CollectNodes<T>(pred);
            wrapped = collector;
        }

        public CollectNodesBuilder<T> skipping(Predicate<? super Visitable> p){
            wrapped = new SkippingVisitor(wrapped, p);
            return this;
        }

        public CollectNodesBuilder<T> until(Predicate<? super Visitable> p){
            wrapped = new VisitUntilVisitor(wrapped, p);
            return this;
        }

        public CollectNodesBuilder<T> onAxis(Predicate<? super Visitable> p){
            wrapped = new AxisVisitor(wrapped, p);
            return this;
        }

        public List<T> collect(Visitable node) throws StandardException {
            node.accept(wrapped);
            return collector.getCollected();
        }
    }

}
