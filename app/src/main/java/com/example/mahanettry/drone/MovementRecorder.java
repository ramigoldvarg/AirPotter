package com.example.mahanettry.drone;

import java.util.Stack;

/**
 * Created by power on 5/9/2018.
 */

public class MovementRecorder {
    private Stack<Movement> movementStack;

    public MovementRecorder() {
        this.movementStack = new Stack<Movement>();
    }

    public void addMovement(Movement mv) {
        this.movementStack.push(mv);
    }

    public Movement removeLatestMovement() {
        return this.movementStack.pop();
    }

    public boolean isEmpty() {
        return this.movementStack.isEmpty();
    }

    public Movement[] retraceTrack() {
        Movement[] retrace = new Movement[this.movementStack.size()];
        Stack<Movement> tempStack = new Stack<Movement>();
        int index = 0;
        while(!this.movementStack.isEmpty()) {
            Movement currMovement = this.movementStack.pop();
            tempStack.push(currMovement);
            retrace[index++] = currMovement.getOppositeMovement();
        }

        while(!this.movementStack.isEmpty()) {
            tempStack.push(this.movementStack.pop());
        }

        return retrace;
    }

    public void reset() {
        while (!this.movementStack.isEmpty()) {
            this.movementStack.pop();
        }
    }
}
