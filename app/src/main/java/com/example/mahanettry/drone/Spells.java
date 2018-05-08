package com.example.mahanettry.drone;

import java.util.HashMap;

/**
 * Created by power on 5/8/2018.
 */

public class Spells {
    private HashMap<String, int[]> spells;
    private String land;
    private String takeoff;

    public Spells(Readable rd) {
        spells = new HashMap<String, int[]>();
        String currLine = new String();

        try {
            rd.readLine(land);
            rd.readLine(takeoff);

            while (rd.readLine(currLine) && !currLine.equals("END")) {
                String movements = new String();
                if(rd.readLine(movements)) {
                    String[] movementsAfterSplit = movements.split(" ");
                    int movementsValues[] = new int[movementsAfterSplit.length];
                    for(int index = 0; index < movementsAfterSplit.length; index++) {
                        movementsValues[index] = Integer.parseInt(movementsAfterSplit[index]);
                    }

                    spells.put(currLine, movementsValues);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String getTakeOffSpell() {
        return this.takeoff;
    }

    public String getLandSpell() {
        return this.land;
    }

    public int[] getSpell(String spellName) throws Exception {
        if(spells.containsKey(spellName)) {
            return spells.get(spellName);
        } else {
            throw new Exception("Spell name not found");
        }
    }
}
