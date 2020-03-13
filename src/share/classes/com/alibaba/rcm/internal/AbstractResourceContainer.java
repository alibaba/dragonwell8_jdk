/*
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package com.alibaba.rcm.internal;

import com.alibaba.rcm.Constraint;
import com.alibaba.rcm.ResourceContainer;
import sun.misc.SharedSecrets;

import java.util.Collections;

/**
 * A skeletal implementation of {@link ResourceContainer} that practices
 * the attach/detach paradigm described in {@link ResourceContainer#run(Runnable)}.
 * <p>
 * Each {@code ResourceContainer} implementation must inherit from this class.
 *
 * @see ResourceContainer#run(Runnable)
 */

public abstract class AbstractResourceContainer implements ResourceContainer {

    protected final static AbstractResourceContainer ROOT = new RootContainer();

    public static AbstractResourceContainer root() {
        return ROOT;
    }

    public static AbstractResourceContainer current() {
        if (!sun.misc.VM.isBooted()) {
            return root();
        }
        return SharedSecrets.getJavaLangAccess().getResourceContainer(Thread.currentThread());
    }

    @Override
    public void run(Runnable command) {
        if (getState() != State.RUNNING) {
            throw new IllegalStateException("container not running");
        }
        ResourceContainer container = current();
        if (container == this) {
            command.run();
            return;
        }
        if (container != root()) {
            throw new IllegalStateException("must be in root container " +
                    "before running into non-root container.");
        }
        attach();
        try {
            command.run();
        } finally {
            detach();
        }
    }

    /**
     * Attach to this resource container.
     * Ensure {@link ResourceContainer#current()} as a root container
     * before calling this method.
     * <p>
     * The implementation class must call {@code super.attach()} to coordinate
     * with {@link ResourceContainer#current()}
     */
    protected void attach() {
        SharedSecrets.getJavaLangAccess().setResourceContainer(Thread.currentThread(), this);
    }

    /**
     * Detach from this resource container and return to root container.
     * <p>
     * The implementation class must call {@code super.detach()} to coordinate
     * with {@link ResourceContainer#current()}
     */
    protected void detach() {
        SharedSecrets.getJavaLangAccess().setResourceContainer(Thread.currentThread(), root());
    }

    private static class RootContainer extends AbstractResourceContainer {
        @Override
        public void run(Runnable command) {
            AbstractResourceContainer container = current();
            if (container == root()) {
                command.run();
                return;
            }
            container.detach();
            try {
                command.run();
            } finally {
                container.attach();
            }
        }

        @Override
        public ResourceContainer.State getState() {
            return ResourceContainer.State.RUNNING;
        }

        @Override
        protected void attach() {
            throw new UnsupportedOperationException("should not reach here");
        }

        @Override
        protected void detach() {
            throw new UnsupportedOperationException("should not reach here");
        }

        @Override
        public void updateConstraint(Constraint constraint) {
            throw new UnsupportedOperationException("updateConstraint() is not supported by root container");
        }

        @Override
        public Iterable<Constraint> getConstraints() {
            return Collections.emptyList();
        }

        @Override
        public void destroy() {
            throw new UnsupportedOperationException("destroy() is not supported by root container");
        }
    }
}
