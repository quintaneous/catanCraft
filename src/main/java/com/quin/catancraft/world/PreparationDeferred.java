package com.quin.catancraft.world;

import java.io.IOException;

/** A proven transient prerequisite, not a source/journal/ownership conflict. */
final class PreparationDeferred extends IOException {
    PreparationDeferred(String message) { super(message); }
    static boolean causedBy(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause())
            if (t instanceof PreparationDeferred) return true;
        return false;
    }
}
