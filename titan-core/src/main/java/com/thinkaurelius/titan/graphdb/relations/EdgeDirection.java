package com.thinkaurelius.titan.graphdb.relations;

import com.google.common.base.Preconditions;
import com.tinkerpop.blueprints.Direction;

/**
 * IMPORTANT: The byte values of the proper directions must be sequential,
 * i.e. the byte values of proper and improper directions may NOT be mixed.
 * This is crucial IN the retrieval for proper edges where we make this assumption.
 *
 * @author Matthias Broecheler (me@matthiasb.com);
 */
public class EdgeDirection {


    public static final boolean impliedBy(Direction sub, Direction sup) {
        return sup==sub || sup==Direction.BOTH;
    }

    public static final Direction fromPosition(int pos) {
        if (pos==0) return Direction.OUT;
        else if (pos==1) return Direction.IN;
        else throw new IllegalArgumentException("Invalid position:" + pos);
    }

    public static final int position(Direction dir) {
        if (dir==Direction.OUT) return 0;
        else if (dir==Direction.IN) return 1;
        else throw new IllegalArgumentException("Invalid direction: " + dir);
    }

    public static final byte getID(Direction dir) {
        return (byte)(position(dir)+1);
    }


    public final static Direction fromID(long dir) {
        return fromID((int) dir);
    }


    public final static Direction fromID(int dir) {
        return fromPosition(dir-1);
    }



}
