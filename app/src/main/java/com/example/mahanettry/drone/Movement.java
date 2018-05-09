package com.example.mahanettry.drone;

/**
 * Created by power on 5/9/2018.
 */

public class Movement {
    private long startTime;
    private long endTime;
    private int[] directionValues;

    public Movement(long startTime, long endTime, int[] directionValues) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.directionValues = directionValues;
    }

    public Movement(long startTime, int[] directionValues) {
        this.startTime = startTime;
        this.directionValues = directionValues;
    }

    public long getMovementTime() {
        return (this.endTime - this.startTime);
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public Movement getOppositeMovement() {
        int oppositeDirection[] = new int[directionValues.length];
        for(int index = 0; index < directionValues.length; index++) {
            oppositeDirection[index] = -this.directionValues[index];
        }

        return new Movement(this.startTime, this.endTime, oppositeDirection);
    }

    public int[] getDirection() {
        return this.directionValues;
    }
}
