/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pgssoft.async_event_bus;

import android.support.annotation.NonNull;

/**
 * Wraps an event that was posted, but which had no subscribers and thus could not be delivered.
 * Immutable;
 * <p/>
 * <p>Subscribing a DeadEvent handler is useful for debugging or logging, as it can detect misconfigurations in a
 * system's event distribution.
 */
public class DeadEvent {

    @NonNull
    public final EventBus source;
    @NonNull
    public final Object event;

    /**
     * Creates a new DeadEvent.
     * Package access, only bus can make instances of this event.
     *
     * @param source bus processing the DeadEvent.
     * @param event  the event that could not be delivered.
     */
    /*package*/ DeadEvent(@NonNull EventBus source, @NonNull Object event) {
        this.source = source;
        this.event = event;
    }

}
