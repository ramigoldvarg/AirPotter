package com.example.mahanettry.drone;

/**
 * Created by power on 5/8/2018.
 */

public class DroneDecorator {
    private MiniDrone drone;
    private int roll;
    private int yaw;
    private int gaz;
    private int pitch;

    public DroneDecorator(MiniDrone drone) {
        this.drone = drone;
    }

    public void stopCurrentMove() {
        this.roll = 0;
        this.yaw = 0;
        this.gaz = 0;
        this.pitch = 0;
        this.drone.setPitch((byte)this.pitch);
        this.drone.setRoll((byte)this.roll);
        this.drone.setGaz((byte) this.gaz);
        this.drone.setYaw((byte)this.yaw);
        this.drone.setFlag((byte) 0);
    }

    public void startNewMovement(int yawRollGazPitch[]) {
        this.pitch = yawRollGazPitch[3];
        this.gaz = yawRollGazPitch[2];
        this.roll = yawRollGazPitch[1];
        this.yaw = yawRollGazPitch[0];
        this.drone.setPitch((byte)this.pitch);
        this.drone.setRoll((byte)this.roll);
        this.drone.setGaz((byte) this.gaz);
        this.drone.setYaw((byte)this.yaw);

        if (this.pitch != 0 || this.roll != 0) {
            this.drone.setFlag((byte) 1);
        } else {
            this.drone.setFlag((byte) 0);
        }
    }

    public void takeoff() {
        drone.takeOff();
    }

    public void land() {
        drone.land();
    }

    public void preformTrack(Movement[] track) {
        for(int index = 0; index < track.length; index++) {
            this.startNewMovement(track[index].getDirection());

            try {
                Thread.sleep(track[index].getMovementTime());
//                this.stopCurrentMove();
//                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
