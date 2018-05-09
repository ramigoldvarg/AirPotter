package com.example.mahanettry.drone;

import java.util.HashMap;

/**
 * Created by power on 5/8/2018.
 */

public class Spells {
    private HashMap<String, int[]> spells;
    private String land;
    private String takeoff;
    private String retrace;
    private String shoot;
    private String dance;

    public Spells(Readable rd) {
        this.spells = new HashMap<String, int[]>();
        this.takeoff = "wingardium leviosa";
        this.land = "avada kedavra";
        this.retrace = "back to the future";
        this.shoot = "expelliarmus";
        this.dance = "dance";

        int accio[] = {0, 0, 0, -50};
        this.spells.put("accio", accio);
        this.spells.put("ikea", accio);

        int obliviate[] = {0, 0, 0, 50};
        this.spells.put("obliviate", obliviate);

        int lumos[] = {0, 0, 50, 0};
        this.spells.put("lumos", lumos);

        int sectumspmera[] = {0,0,-50,0};
        this.spells.put("sectumsempra", sectumspmera);

        int alohomora[] = {0, 50, 0, 0};
        this.spells.put("alohomora", alohomora);

        int expecto[] = {0, -50, 0, 0};
        this.spells.put("expecto", expecto);

        int ridikulus[] = {50, 0, 0, 0};
        this.spells.put("ridiculous", ridikulus);

//        try {
//            rd.readLine(land);
//            rd.readLine(takeoff);
//
//            while (rd.readLine(currLine) && !currLine.equals("END")) {
//                String movements = new String();
//                if(rd.readLine(movements)) {
//                    String[] movementsAfterSplit = movements.split(" ");
//                    int movementsValues[] = new int[movementsAfterSplit.length];
//                    for(int index = 0; index < movementsAfterSplit.length; index++) {
//                        movementsValues[index] = Integer.parseInt(movementsAfterSplit[index]);
//                    }
//
//                    spells.put(currLine, movementsValues);
//                }
//            }
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
    }

    public String getTakeOffSpell() {
        return this.takeoff;
    }

    public String getLandSpell() {
        return this.land;
    }

    public String getRetraceSpell() {
        return this.retrace;
    }

    public int[] getSpell(String spellName) throws Exception {
        if(this.spells.containsKey(spellName)) {
            return this.spells.get(spellName);
        } else {
            throw new Exception("Spell name not found");
        }
    }

    public String getShoot() {
        return this.shoot;
    }

    public String getDance() {
        return this.dance;
    }
}
