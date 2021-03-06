package squidpony.squidai;

import squidpony.squidgrid.Direction;
import squidpony.squidgrid.LOS;
import squidpony.squidmath.Coord;
import squidpony.squidmath.LightRNG;
import squidpony.squidmath.RNG;


import java.util.*;

/**
 * An alternative to AStarSearch when you want to fully explore a search space, or when you want a gradient floodfill.
 * If you can't remember how to spell this, just remember: Does It Just Know Stuff? That's Really Awesome!
 * Created by Tommy Ettinger on 4/4/2015.
 */
public class DijkstraMap
{
    /**
     * The type of heuristic to use.
     */
    public enum Measurement {

        /**
         * The distance it takes when only the four primary directions can be
         * moved in. The default.
         */
        MANHATTAN,
        /**
         * The distance it takes when diagonal movement costs the same as
         * cardinal movement.
         */
        CHEBYSHEV,
        /**
         * The distance it takes as the crow flies. This will NOT affect movement cost when calculating a path,
         * only the preferred squares to travel to (resulting in drastically more reasonable-looking paths).
         */
        EUCLIDEAN
    }

    /**
     * This affects how distance is measured on diagonal directions vs. orthogonal directions. MANHATTAN should form a
     * diamond shape on a featureless map, while CHEBYSHEV and EUCLIDEAN will form a square. EUCLIDEAN does not affect
     * the length of paths, though it will change the DijkstraMap's gradientMap to have many non-integer values, and
     * that in turn will make paths this finds much more realistic and smooth (favoring orthogonal directions unless a
     * diagonal one is a better option).
     */
    public Measurement measurement = Measurement.MANHATTAN;


    /**
     * Stores which parts of the map are accessible and which are not. Should not be changed unless the actual physical
     * terrain has changed. You should call initialize() with a new map instead of changing this directly.
     */
    public double[][] physicalMap;
    /**
     * The frequently-changing values that are often the point of using this class; goals will have a value of 0, and
     * any cells that can have a character reach a goal in n steps will have a value of n. Cells that cannot be
     * entered because they are solid will have a very high value equal to the WALL constant in this class, and cells
     * that cannot be entered because they cannot reach a goal will have a different very high value equal to the
     * DARK constant in this class.
     */
    public double[][] gradientMap;
    /**
     * This stores the entry cost multipliers for each cell; that is, a value of 1.0 is a normal, unmodified cell, but
     * a value of 0.5 can be entered easily (two cells of its cost can be entered for the cost of one 1.0 cell), and a
     * value of 2.0 can only be entered with difficulty (one cell of its cost can be entered for the cost of two 1.0
     * cells). Unlike the measurement field, this does affect the length of paths, as well as the numbers assigned
     * to gradientMap during a scan. The values for walls are identical to the value used by gradientMap, that is, this
     * class' WALL static final field. Floors, however, are never given FLOOR as a value, and default to 1.0 .
     *
     */
    public double[][] costMap = null;
    /**
     * Height of the map. Exciting stuff. Don't change this, instead call initialize().
     */
    public int height;
    /**
     * Width of the map. Exciting stuff. Don't change this, instead call initialize().
     */
    public int width;
    /**
     * The latest path that was obtained by calling findPath(). It will not contain the value passed as a starting
     * cell; only steps that require movement will be included, and so if the path has not been found or a valid
     * path toward a goal is impossible, this ArrayList will be empty.
     */
    public ArrayList<Coord> path = new ArrayList<Coord>();
    /**
     * Goals are always marked with 0.
     */
    public static final double GOAL = 0.0;
    /**
     * Floor cells, which include any walkable cell, are marked with a high number equal to 999200.0 .
     */
    public static final double FLOOR = 999200.0;
    /**
     * Walls, which are solid no-entry cells, are marked with a high number equal to 999500.0 .
     */
    public static final double WALL = 999500.0;
    /**
     * This is used to mark cells that the scan couldn't reach, and these dark cells are marked with a high number
     * equal to 999800.0 .
     */
    public static final double DARK = 999800.0;
    /**
     * Goals that pathfinding will seek out. The Double value should almost always be 0.0 , the same as the static GOAL
     * constant in this class.
     */
    public LinkedHashMap<Coord, Double> goals;
    private LinkedHashMap<Coord, Double> fresh, closed, open;
    /**
     * The RNG used to decide which one of multiple equally-short paths to take.
     */
    public RNG rng;
    private int frustration = 0;
    public Coord[][] targetMap;


    private boolean initialized = false;


    private int mappedCount = 0;

    public int getMappedCount() {
        return mappedCount;
    }
    /**
     * Construct a DijkstraMap without a level to actually scan. If you use this constructor, you must call an
     * initialize() method before using this class.
     */
    public DijkstraMap() {
        rng = new RNG(new LightRNG());
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
    }

    /**
     * Construct a DijkstraMap without a level to actually scan. This constructor allows you to specify an RNG before
     * it is ever used in this class. If you use this constructor, you must call an initialize() method before using
     * any other methods in the class.
     */
    public DijkstraMap(RNG random) {
        rng = random;
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
    }

    /**
     * Used to construct a DijkstraMap from the output of another.
     * @param level
     */
    public DijkstraMap(final double[][] level) {
        rng = new RNG(new LightRNG());
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }
    /**
     * Used to construct a DijkstraMap from the output of another, specifying a distance calculation.
     * @param level
     * @param measurement
     */
    public DijkstraMap(final double[][] level, Measurement measurement) {
        rng = new RNG(new LightRNG());
        this.measurement = measurement;
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }

    /**
     * Constructor meant to take a char[][] returned by DungeonGen.generate(), or any other
     * char[][] where '#' means a wall and anything else is a walkable tile. If you only have
     * a map that uses box-drawing characters, use DungeonUtility.linesToHashes() to get a
     * map that can be used here.
     *
     * @param level
     */
    public DijkstraMap(final char[][] level) {
        rng = new RNG(new LightRNG());
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }

    /**
     * Constructor meant to take a char[][] returned by DungeonGen.generate(), or any other
     * char[][] where '#' means a wall and anything else is a walkable tile. If you only have
     * a map that uses box-drawing characters, use DungeonUtility.linesToHashes() to get a
     * map that can be used here. Also takes an RNG that ensures predictable path choices given
     * otherwise identical inputs and circumstances.
     *
     * @param level
     * @param rng The RNG to use for certain decisions; only affects find* methods like findPath, not scan.
     */
    public DijkstraMap(final char[][] level, RNG rng) {
        this.rng = rng;
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }
    /**
     * Constructor meant to take a char[][] returned by DungeonGen.generate(), or any other
     * char[][] where one char means a wall and anything else is a walkable tile. If you only have
     * a map that uses box-drawing characters, use DungeonUtility.linesToHashes() to get a
     * map that can be used here. You can specify the character used for walls.
     *
     * @param level
     */
    public DijkstraMap(final char[][] level, char alternateWall) {
        rng = new RNG(new LightRNG());
        path = new ArrayList<Coord>();

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level, alternateWall);
    }

    /**
     * Constructor meant to take a char[][] returned by DungeonGen.generate(), or any other
     * char[][] where '#' means a wall and anything else is a walkable tile. If you only have
     * a map that uses box-drawing characters, use DungeonUtility.linesToHashes() to get a
     * map that can be used here. This constructor specifies a distance measurement.
     *
     * @param level
     * @param measurement
     */
    public DijkstraMap(final char[][] level, Measurement measurement) {
        rng = new RNG(new LightRNG());
        path = new ArrayList<Coord>();
        this.measurement = measurement;

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }

    /**
     * Constructor meant to take a char[][] returned by DungeonGen.generate(), or any other
     * char[][] where '#' means a wall and anything else is a walkable tile. If you only have
     * a map that uses box-drawing characters, use DungeonUtility.linesToHashes() to get a
     * map that can be used here. Also takes a distance measurement and an RNG that ensures
     * predictable path choices given otherwise identical inputs and circumstances.
     *
     * @param level
     * @param rng The RNG to use for certain decisions; only affects find* methods like findPath, not scan.
     */
    public DijkstraMap(final char[][] level, Measurement measurement, RNG rng) {
        this.rng = rng;
        path = new ArrayList<Coord>();
        this.measurement = measurement;

        goals = new LinkedHashMap<Coord, Double>();
        fresh = new LinkedHashMap<Coord, Double>();
        closed = new LinkedHashMap<Coord, Double>();
        open = new LinkedHashMap<Coord, Double>();
        initialize(level);
    }
    /**
     * Used to initialize or re-initialize a DijkstraMap that needs a new PhysicalMap because it either wasn't given
     * one when it was constructed, or because the contents of the terrain have changed permanently (not if a
     * creature moved; for that you pass the positions of creatures that block paths to scan() or findPath() ).
     * @param level
     * @return
     */
    public DijkstraMap initialize(final double[][] level) {
        width = level.length;
        height = level[0].length;
        gradientMap = new double[width][height];
        physicalMap = new double[width][height];
        costMap = new double[width][height];
        targetMap = new Coord[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(level[x], 0, gradientMap[x],0, height);
            System.arraycopy(level[x], 0, physicalMap[x],0, height);
            Arrays.fill(costMap[x], 1.0);
        }
        initialized = true;
        return this;
    }

    /**
     * Used to initialize or re-initialize a DijkstraMap that needs a new PhysicalMap because it either wasn't given
     * one when it was constructed, or because the contents of the terrain have changed permanently (not if a
     * creature moved; for that you pass the positions of creatures that block paths to scan() or findPath() ).
     * @param level
     * @return
     */
    public DijkstraMap initialize(final char[][] level) {
        width = level.length;
        height = level[0].length;
        gradientMap = new double[width][height];
        physicalMap = new double[width][height];
        costMap = new double[width][height];
        targetMap = new Coord[width][height];
        for (int x = 0; x < width; x++) {
            Arrays.fill(costMap[x], 1.0);
            for (int y = 0; y < height; y++) {
                double t = (level[x][y] == '#') ? WALL : FLOOR;
                gradientMap[x][y] = t;
                physicalMap[x][y] = t;
            }
        }
        initialized = true;
        return this;
    }

    /**
     * Used to initialize or re-initialize a DijkstraMap that needs a new PhysicalMap because it either wasn't given
     * one when it was constructed, or because the contents of the terrain have changed permanently (not if a
     * creature moved; for that you pass the positions of creatures that block paths to scan() or findPath() ). This
     * initialize() method allows you to specify an alternate wall char other than the default character, '#' .
     * @param level
     * @param alternateWall
     * @return
     */
    public DijkstraMap initialize(final char[][] level, char alternateWall) {
        width = level.length;
        height = level[0].length;
        gradientMap = new double[width][height];
        physicalMap = new double[width][height];
        costMap = new double[width][height];
        targetMap = new Coord[width][height];
        for (int x = 0; x < width; x++) {
            Arrays.fill(costMap[x], 1.0);
            for (int y = 0; y < height; y++) {
                double t = (level[x][y] == alternateWall) ? WALL : FLOOR;
                gradientMap[x][y] = t;
                physicalMap[x][y] = t;
            }
        }
        initialized = true;
        return this;
    }
    /**
     * Used to initialize the entry cost modifiers for games that require variable costs to enter squares. This expects
     * a char[][] of the same exact dimensions as the 2D array that was used to previously initialize() this
     * DijkstraMap, treating the '#' char as a wall (impassable) and anything else as having a normal cost to enter.
     * The costs can be accessed later by using costMap directly (which will have a valid value when this does not
     * throw an exception), or by calling setCost().
     * @param level a 2D char array that uses '#' for walls
     * @return this DijkstraMap for chaining.
     */
    public DijkstraMap initializeCost(final char[][] level) {
        if(!initialized) throw new IllegalStateException("DijkstraMap must be initialized first!");
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                costMap[x][y] = (level[x][y] == '#') ? WALL : 1.0;
            }
        }
        return this;
    }
    /**
     * Used to initialize the entry cost modifiers for games that require variable costs to enter squares. This expects
     * a char[][] of the same exact dimensions as the 2D array that was used to previously initialize() this
     * DijkstraMap, treating the '#' char as a wall (impassable) and anything else as having a normal cost to enter.
     * The costs can be accessed later by using costMap directly (which will have a valid value when this does not
     * throw an exception), or by calling setCost().
     *
     * This method allows you to specify an alternate wall char other than the default character, '#' .
     * @param level a 2D char array that uses alternateChar for walls.
     * @param alternateWall a char to use to represent walls.
     * @return this DijkstraMap for chaining.
     */
    public DijkstraMap initializeCost(final char[][] level, char alternateWall) {
        if(!initialized) throw new IllegalStateException("DijkstraMap must be initialized first!");
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                costMap[x][y] = (level[x][y] == alternateWall) ? WALL : 1.0;
            }
        }
        return this;
    }
    /**
     * Used to initialize the entry cost modifiers for games that require variable costs to enter squares. This expects
     * a double[][] of the same exact dimensions as the 2D array that was used to previously initialize() this
     * DijkstraMap, using the exact values given in costs as the values to enter cells, even if they aren't what this
     * class would assign normally -- walls and other impassable values should be given WALL as a value, however.
     * The costs can be accessed later by using costMap directly (which will have a valid value when this does not
     * throw an exception), or by calling setCost().
     *
     * This method should be slightly more efficient than the other initializeCost methods.
     * @param costs a 2D double array that already has the desired cost values
     * @return this DijkstraMap for chaining.
     */
    public DijkstraMap initializeCost(final double[][] costs) {
        if(!initialized) throw new IllegalStateException("DijkstraMap must be initialized first!");
        costMap = new double[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(costs[x], 0, costMap[x], 0, height);
        }
        return this;
    }

    /**
     * Resets the gradientMap to its original value from physicalMap.
     */
    public void resetMap() {
        if (!initialized) return;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                gradientMap[x][y] = physicalMap[x][y];
            }
        }
    }

    /**
     * Resets the targetMap (which is only assigned in the first place if you use findTechniquePath() ).
     */
    public void resetTargetMap() {
        if (!initialized) return;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                targetMap[x][y] = null;
            }
        }
    }

    /**
     * Resets this DijkstraMap to a state with no goals, no discovered path, and no changes made to gradientMap
     * relative to physicalMap.
     */
    public void reset() {
        resetMap();
        goals.clear();
        path.clear();
        closed.clear();
        fresh.clear();
        open.clear();
        frustration = 0;
    }

    /**
     * Marks a cell as a goal for pathfinding, unless the cell is a wall or unreachable area (then it does nothing).
     * @param x
     * @param y
     */
    public void setGoal(int x, int y) {
        if(!initialized) return;
        if (physicalMap[x][y] > FLOOR) {
            return;
        }

        goals.put(Coord.get(x, y), GOAL);
    }

    /**
     * Marks a cell as a goal for pathfinding, unless the cell is a wall or unreachable area (then it does nothing).
     * @param pt
     */
    public void setGoal(Coord pt) {
        if(!initialized) return;
        if (physicalMap[pt.x][pt.y] > FLOOR) {
            return;
        }

        goals.put(pt, GOAL);
    }
    /**
     * Marks a cell's cost for pathfinding as cost, unless the cell is a wall or unreachable area (then it always sets
     * the cost to the value of the WALL field).
     * @param pt
     * @param cost
     */
    public void setCost(Coord pt, double cost) {
        if(!initialized) return;
        if (physicalMap[pt.x][pt.y] > FLOOR) {
            costMap[pt.x][pt.y] = WALL;
            return;
        }
        costMap[pt.x][pt.y] = cost;
    }
    /**
     * Marks a cell's cost for pathfinding as cost, unless the cell is a wall or unreachable area (then it always sets
     * the cost to the value of the WALL field).
     * @param x
     * @param y
     * @param cost
     */
    public void setCost(int x, int y, double cost) {
        if(!initialized) return;
        if (physicalMap[x][y] > FLOOR) {
            costMap[x][y] = WALL;
            return;
        }
        costMap[x][y] = cost;
    }

    /**
     * Marks a specific cell in gradientMap as completely impossible to enter.
     * @param x
     * @param y
     */
    public void setOccupied(int x, int y) {
        if(!initialized) return;
        gradientMap[x][y] = WALL;
    }

    /**
     * Reverts a cell to the value stored in the original state of the level as known by physicalMap.
     * @param x
     * @param y
     */
    public void resetCell(int x, int y) {
        if(!initialized) return;
        gradientMap[x][y] = physicalMap[x][y];
    }

    /**
     * Reverts a cell to the value stored in the original state of the level as known by physicalMap.
     * @param pt
     */
    public void resetCell(Coord pt) {
        if(!initialized) return;
        gradientMap[pt.x][pt.y] = physicalMap[pt.x][pt.y];
    }

    /**
     * Used to remove all goals and undo any changes to gradientMap made by having a goal present.
     */
    public void clearGoals() {
        if(!initialized)
            return;
        for (Map.Entry<Coord, Double> entry : goals.entrySet()) {
            resetCell(entry.getKey());
        }
        goals.clear();
    }

    protected void setFresh(int x, int y, double counter) {
        if(!initialized) return;
        gradientMap[x][y] = counter;
        fresh.put(Coord.get(x, y), counter);
    }

    protected void setFresh(final Coord pt, double counter) {
        if(!initialized) return;
        gradientMap[pt.x][pt.y] = counter;
        fresh.put(Coord.get(pt.x, pt.y), counter);
    }

    /**
     * Recalculate the Dijkstra map and return it. Cells that were marked as goals with setGoal will have
     * a value of 0, the cells adjacent to goals will have a value of 1, and cells progressively further
     * from goals will have a value equal to the distance from the nearest goal. The exceptions are walls,
     * which will have a value defined by the WALL constant in this class, and areas that the scan was
     * unable to reach, which will have a value defined by the DARK constant in this class (typically,
     * these areas should not be used to place NPCs or items and should be filled with walls). This uses the
     * current measurement.
     *
     * @param impassable A Set of Position keys representing the locations of enemies or other moving obstacles to a
     *                   path that cannot be moved through; this can be null if there are no such obstacles.
     * @return A 2D double[width][height] using the width and height of what this knows about the physical map.
     */
    public double[][] scan(Set<Coord> impassable) {
        if(!initialized) return null;
        if(impassable == null)
            impassable = new LinkedHashSet<Coord>();
        LinkedHashMap<Coord, Double> blocking = new LinkedHashMap<Coord, Double>(impassable.size());
        for (Coord pt : impassable) {
            blocking.put(pt, WALL);
        }
        closed.putAll(blocking);

        for (Map.Entry<Coord, Double> entry : goals.entrySet()) {
            if (closed.containsKey(entry.getKey()))
                closed.remove(entry.getKey());
            gradientMap[entry.getKey().x][entry.getKey().y] = entry.getValue();
        }
        double currentLowest = 999000;
        LinkedHashMap<Coord, Double> lowest = new LinkedHashMap<Coord, Double>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (gradientMap[x][y] > FLOOR && !goals.containsKey(Coord.get(x, y)))
                    closed.put(Coord.get(x, y), physicalMap[x][y]);
                else if(gradientMap[x][y] < currentLowest)
                {
                    currentLowest = gradientMap[x][y];
                    lowest.clear();
                    lowest.put(Coord.get(x, y), currentLowest);
                }
                else if(gradientMap[x][y] == currentLowest)
                {
                    lowest.put(Coord.get(x, y), currentLowest);
                }
            }
        }
        int numAssigned = lowest.size();
        mappedCount = goals.size();
        open.putAll(lowest);
        Direction[] dirs = (measurement == Measurement.MANHATTAN) ? Direction.CARDINALS : Direction.OUTWARDS;
        while (numAssigned > 0) {
//            ++iter;
            numAssigned = 0;

            for (Map.Entry<Coord, Double> cell : open.entrySet()) {
                for (int d = 0; d < dirs.length; d++) {
                    Coord adj = cell.getKey().translate(dirs[d].deltaX, dirs[d].deltaY);
                    double h = heuristic(dirs[d]);
                    if (!closed.containsKey(adj) && !open.containsKey(adj) && gradientMap[cell.getKey().x][cell.getKey().y] + h * costMap[adj.x][adj.y] < gradientMap[adj.x][adj.y]) {
                        setFresh(adj, cell.getValue() + h * costMap[adj.x][adj.y]);
                        ++numAssigned;
                        ++mappedCount;
                    }
                }
            }
//            closed.putAll(open);
            open = new LinkedHashMap<Coord, Double>(fresh);
            fresh.clear();
        }
        closed.clear();
        open.clear();

        double[][] gradientClone = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (gradientMap[x][y] == FLOOR) {
                    gradientMap[x][y] = DARK;
                }
            }
            System.arraycopy(gradientMap[x], 0, gradientClone[x], 0, height);
        }

        return gradientClone;
    }

    /**
     * Recalculate the Dijkstra map up to a limit and return it. Cells that were marked as goals with setGoal will have
     * a value of 0, the cells adjacent to goals will have a value of 1, and cells progressively further
     * from goals will have a value equal to the distance from the nearest goal. If a cell would take more steps to
     * reach than the given limit, it will have a value of DARK if it was passable instead of the distance. The
     * exceptions are walls, which will have a value defined by the WALL constant in this class, and areas that the scan
     * was unable to reach, which will have a value defined by the DARK constant in this class. This uses the
     * current measurement.
     *
     * @param limit The maximum number of steps to scan outward from a goal.
     * @param impassable A Set of Position keys representing the locations of enemies or other moving obstacles to a
     *                   path that cannot be moved through; this can be null if there are no such obstacles.
     * @return A 2D double[width][height] using the width and height of what this knows about the physical map.
     */
    public double[][] partialScan(int limit, Set<Coord> impassable) {
        if(!initialized) return null;
        if(impassable == null)
            impassable = new LinkedHashSet<Coord>();
        LinkedHashMap<Coord, Double> blocking = new LinkedHashMap<Coord, Double>(impassable.size());
        for (Coord pt : impassable) {
            blocking.put(pt, WALL);
        }
        closed.putAll(blocking);

        for (Map.Entry<Coord, Double> entry : goals.entrySet()) {
            if (closed.containsKey(entry.getKey()))
                closed.remove(entry.getKey());
            gradientMap[entry.getKey().x][entry.getKey().y] = entry.getValue();
        }
        double currentLowest = 999000;
        LinkedHashMap<Coord, Double> lowest = new LinkedHashMap<Coord, Double>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (gradientMap[x][y] > FLOOR && !goals.containsKey(Coord.get(x, y)))
                    closed.put(Coord.get(x, y), physicalMap[x][y]);
                else if(gradientMap[x][y] < currentLowest)
                {
                    currentLowest = gradientMap[x][y];
                    lowest.clear();
                    lowest.put(Coord.get(x, y), currentLowest);
                }
                else if(gradientMap[x][y] == currentLowest)
                {
                    lowest.put(Coord.get(x, y), currentLowest);
                }
            }
        }
        int numAssigned = lowest.size();
        mappedCount = goals.size();
        open.putAll(lowest);

        Direction[] dirs = (measurement == Measurement.MANHATTAN) ? Direction.CARDINALS : Direction.OUTWARDS;
        int iter = 0;
        while (numAssigned > 0 && iter < limit) {
//            ++iter;
            numAssigned = 0;

            for (Map.Entry<Coord, Double> cell : open.entrySet()) {
                for (int d = 0; d < dirs.length; d++) {
                    Coord adj = cell.getKey().translate(dirs[d].deltaX, dirs[d].deltaY);
                    double h = heuristic(dirs[d]);
                    if (!closed.containsKey(adj) && !open.containsKey(adj) && gradientMap[cell.getKey().x][cell.getKey().y] + h * costMap[adj.x][adj.y] < gradientMap[adj.x][adj.y]) {
                        setFresh(adj, cell.getValue() + h * costMap[adj.x][adj.y]);
                        ++numAssigned;
                        ++mappedCount;
                    }
                }
            }
//            closed.putAll(open);
            open = new LinkedHashMap<Coord, Double>(fresh);
            fresh.clear();
            ++iter;
        }
        closed.clear();
        open.clear();


        double[][] gradientClone = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (gradientMap[x][y] == FLOOR) {
                    gradientMap[x][y] = DARK;
                }
            }
            System.arraycopy(gradientMap[x], 0, gradientClone[x], 0, height);
        }
        return gradientClone;
    }

    /**
     * Recalculate the Dijkstra map for a creature that is potentially larger than 1x1 cell and return it. The value of
     * a cell in the returned Dijkstra map assumes that a creature is square, with a side length equal to the passed
     * size, that its minimum-x, minimum-y cell is the starting cell, and that any cell with a distance number
     * represents the distance for the creature's minimum-x, minimum-y cell to reach it. Cells that cannot be entered
     * by the minimum-x, minimum-y cell because of sizing (such as a floor cell next to a maximum-x and/or maximum-y
     * wall if size is &gt; 1) will be marked as DARK. Cells that were marked as goals with setGoal will have
     * a value of 0, the cells adjacent to goals will have a value of 1, and cells progressively further
     * from goals will have a value equal to the distance from the nearest goal. The exceptions are walls,
     * which will have a value defined by the WALL constant in this class, and areas that the scan was
     * unable to reach, which will have a value defined by the DARK constant in this class. (typically,
     * these areas should not be used to place NPCs or items and should be filled with walls). This uses the
     * current measurement.
     *
     * @param impassable A Set of Position keys representing the locations of enemies or other moving obstacles to a
     *                   path that cannot be moved through; this can be null if there are no such obstacles.
     * @param size The length of one side of a square creature using this to find a path, i.e. 2 for a 2x2 cell
     *             creature. Non-square creatures are not supported because turning is really hard.
     * @return A 2D double[width][height] using the width and height of what this knows about the physical map.
     */
    public double[][] scan(Set<Coord> impassable, int size) {
        if(!initialized) return null;
        if(impassable == null)
            impassable = new LinkedHashSet<Coord>();
        LinkedHashMap<Coord, Double> blocking = new LinkedHashMap<Coord, Double>(impassable.size());
        for (Coord pt : impassable) {
            blocking.put(pt, WALL);
            for(int x = 0; x < size; x++)
            {
                for(int y = 0; y < size; y++)
                {
                    if(x + y == 0)
                        continue;
                    if(gradientMap[pt.x - x][pt.y - y] <= FLOOR)
                        blocking.put(Coord.get(pt.x - x, pt.y - y), DARK);
                }
            }
        }
        closed.putAll(blocking);

        for (Map.Entry<Coord, Double> entry : goals.entrySet()) {
            if (closed.containsKey(entry.getKey()))
                closed.remove(entry.getKey());
            gradientMap[entry.getKey().x][entry.getKey().y] = entry.getValue();
        }
        mappedCount = goals.size();
        double currentLowest = 999000;
        LinkedHashMap<Coord, Double> lowest = new LinkedHashMap<Coord, Double>();
        Coord p = Coord.get(0, 0), temp = Coord.get(0, 0);
        for (int y = 0; y < height; y++) {
            I_AM_BECOME_DEATH_DESTROYER_OF_WORLDS:
            for (int x = 0; x < width; x++) {
                p = Coord.get(x, y);
                if (gradientMap[x][y] > FLOOR && !goals.containsKey(p)) {
                    closed.put(p, physicalMap[x][y]);
                    if(gradientMap[x][y] == WALL) {
                        for (int i = 0; i < size; i++) {
                            if (x - i < 0)
                                continue;
                            temp = temp.setX(x - i);
                            for (int j = 0; j < size; j++) {
                                temp = temp.setY(y - j);
                                if (y - j < 0 || closed.containsKey(temp))
                                    continue;
                                if (gradientMap[temp.x][temp.y] <= FLOOR && !goals.containsKey(temp))
                                    closed.put(Coord.get(temp.x, temp.y), DARK);
                            }
                        }
                    }
                }

                else if(gradientMap[x][y] < currentLowest && !closed.containsKey(p))
                {
                    for(int i = 0; i < size; i++)
                    {
                        if(x + i >= width)
                            continue I_AM_BECOME_DEATH_DESTROYER_OF_WORLDS;
                        temp = temp.setX(x + i);
                        for(int j = 0; j < size; j++)
                        {
                            temp = temp.setY(y + j);
                            if(y + j >= height || closed.containsKey(temp))
                                continue I_AM_BECOME_DEATH_DESTROYER_OF_WORLDS;
                        }
                    }

                    currentLowest = gradientMap[x][y];
                    lowest.clear();
                    lowest.put(Coord.get(x, y), currentLowest);

                }
                else if(gradientMap[x][y] == currentLowest && !closed.containsKey(p))
                {
                    if(!closed.containsKey(p)) {
                        for (int i = 0; i < size; i++) {
                            if (x + i >= width)
                                continue I_AM_BECOME_DEATH_DESTROYER_OF_WORLDS;
                            temp = temp.setX(x + i);
                            for (int j = 0; j < size; j++) {
                                temp = temp.setY(y + j);
                                if (y + j >= height || closed.containsKey(temp))
                                    continue I_AM_BECOME_DEATH_DESTROYER_OF_WORLDS;
                            }
                        }
                        lowest.put(Coord.get(x, y), currentLowest);
                    }
                }
            }
        }
        int numAssigned = lowest.size();
        open.putAll(lowest);
        Direction[] dirs = (measurement == Measurement.MANHATTAN) ? Direction.CARDINALS : Direction.OUTWARDS;
        while (numAssigned > 0) {
            numAssigned = 0;
            for (Map.Entry<Coord, Double> cell : open.entrySet()) {
                for (int d = 0; d < dirs.length; d++) {
                    Coord adj = cell.getKey().translate(dirs[d].deltaX, dirs[d].deltaY);
                    double h = heuristic(dirs[d]);
                    if (!closed.containsKey(adj) && !open.containsKey(adj) && gradientMap[cell.getKey().x][cell.getKey().y] + h * costMap[adj.x][adj.y] < gradientMap[adj.x][adj.y]) {
                        setFresh(adj, cell.getValue() + h * costMap[adj.x][adj.y]);
                        ++numAssigned;
                        ++mappedCount;
                    }
                }
            }
//            closed.putAll(open);
            open = new LinkedHashMap<Coord, Double>(fresh);
            fresh.clear();
        }
        closed.clear();
        open.clear();


        double[][] gradientClone = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (gradientMap[x][y] == FLOOR) {
                    gradientMap[x][y] = DARK;
                }
            }
            System.arraycopy(gradientMap[x], 0, gradientClone[x], 0, height);
        }
        return gradientClone;
    }

    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to the closest reachable
     * goal. The maximum length of the returned list is given by length; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     *
     * @param length
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findPath(int length, Set<Coord> impassable,
                                     Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);
        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;
        scan(impassable2);
        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }

            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            path.add(currentPos);
            paidLength += costMap[currentPos.x][currentPos.y];
            frustration++;
            if (paidLength > length - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findPath(length, impassable2, onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to a goal, until preferredRange is
     * reached, or further from a goal if the preferredRange has not been met at the current distance.
     * The maximum length of the returned list is given by moveLength; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     *
     * @param moveLength
     * @param preferredRange
     * @param los a squidgrid.LOS object if the preferredRange should try to stay in line of sight, or null if LoS
     *            should be disregarded.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findAttackPath(int moveLength, int preferredRange, LOS los, Set<Coord> impassable,
                                           Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        if(preferredRange < 0) preferredRange = 0;
        double[][] resMap = new double[width][height];
        if(los != null)
        {
            for(int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    resMap[x][y] = (physicalMap[x][y] == WALL) ? 1.0 : 0.0;
                }
            }
        }
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;

        Measurement mess = measurement;
        if(measurement == Measurement.EUCLIDEAN)
        {
            measurement = Measurement.CHEBYSHEV;
        }
        scan(impassable2);
        goals.clear();

        for(int x = 0; x < width; x++)
        {
            CELL:
            for(int y = 0; y < height; y++)
            {
                if(gradientMap[x][y] == WALL || gradientMap[x][y] == DARK)
                    continue;
                if (gradientMap[x][y] == preferredRange && los != null) {
                    for (Coord goal : targets) {
                        if (los.isReachable(resMap, x, y, goal.x, goal.y)) {
                            setGoal(x, y);
                            gradientMap[x][y] = 0;
                            continue CELL;
                        }
                    }
                    gradientMap[x][y] = FLOOR;
                }
                else
                    gradientMap[x][y] = FLOOR;
            }
        }
        measurement = mess;
        scan(impassable2);

        Coord currentPos = start;
        double paidLength = 0.0;

        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }
            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            path.add(currentPos);
            paidLength += costMap[currentPos.x][currentPos.y];
            frustration++;
            if (paidLength > moveLength - 1.0) {

                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findAttackPath(moveLength, preferredRange, los, impassable2, onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to a goal, until a cell is reached with
     * a distance from a goal that is at least equal to minPreferredRange and no more than maxPreferredRange,
     * which may go further from a goal if the minPreferredRange has not been met at the current distance.
     * The maximum length of the returned list is given by moveLength; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     *
     * @param moveLength
     * @param minPreferredRange
     * @param maxPreferredRange
     * @param los a squidgrid.LOS object if the preferredRange should try to stay in line of sight, or null if LoS
     *            should be disregarded.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findAttackPath(int moveLength, int minPreferredRange, int maxPreferredRange, LOS los,
                                           Set<Coord> impassable, Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        if(minPreferredRange < 0) minPreferredRange = 0;
        if(maxPreferredRange < minPreferredRange) maxPreferredRange = minPreferredRange;
        double[][] resMap = new double[width][height];
        if(los != null)
        {
            for(int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    resMap[x][y] = (physicalMap[x][y] == WALL) ? 1.0 : 0.0;
                }
            }
        }
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);
        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;

        Measurement mess = measurement;
        if(measurement == Measurement.EUCLIDEAN)
        {
            measurement = Measurement.CHEBYSHEV;
        }
        scan(impassable2);
        goals.clear();

        for(int x = 0; x < width; x++)
        {
            CELL:
            for(int y = 0; y < height; y++)
            {
                if(gradientMap[x][y] == WALL || gradientMap[x][y] == DARK)
                    continue;
                if (gradientMap[x][y] >= minPreferredRange && gradientMap[x][y] <= maxPreferredRange) {

                    for (Coord goal : targets) {
                        if (los == null || los.isReachable(resMap, x, y, goal.x, goal.y)) {
                            setGoal(x, y);
                            gradientMap[x][y] = 0;
                            continue CELL;
                        }
                    }
                    gradientMap[x][y] = FLOOR;
                }
                else
                    gradientMap[x][y] = FLOOR;
            }
        }
        measurement = mess;
        scan(impassable2);

        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }

            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            path.add(Coord.get(currentPos.x, currentPos.y));
            paidLength += costMap[currentPos.x][currentPos.y];
            frustration++;
            if (paidLength > moveLength - 1.0) {

                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findAttackPath(moveLength, minPreferredRange, maxPreferredRange, los, impassable2,
                            onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }

    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to a goal, where goals are
     * considered valid if they are at a valid range for the given Technique to hit at least one target
     * and ideal if that Technique can affect as many targets as possible from a cell that can be moved
     * to with at most movelength steps.
     *
     * The return value of this method is the path to get to a location to attack, but on its own it
     * does not tell the user how to perform the attack.  It does set the targetMap 2D Coord array field
     * so that if your position at the end of the returned path is non-null in targetMap, it will be
     * a Coord that can be used as a target position for Technique.apply() . If your position at the end
     * of the returned path is null, then an ideal attack position was not reachable by the path.
     *
     * This needs a char[][] dungeon as an argument because DijkstraMap does not always have a char[][]
     * version of the map available to it, and certain AOE implementations that a Technique uses may
     * need a char[][] specifically to determine what they affect.
     *
     * The maximum length of the returned list is given by moveLength; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in allies
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios, and is also used considered an undesirable thing to affect for the Technique),
     * it will recalculate a move so that it does not pass into that cell.
     *
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a target overlapping one.
     *
     * @param moveLength the maximum distance to try to pathfind out to; if a spot to use a Technique can be found
     *                   while moving no more than this distance, then the targetMap field in this object will have a
     *                   target Coord that is ideal for the given Technique at the x, y indices corresponding to the
     *                   last Coord in the returned path.
     * @param tech a Technique that we will try to find an ideal place to use, and/or a path toward that place.
     * @param dungeon a char 2D array with '#' for walls.
     * @param los a squidgrid.LOS object if the preferred range should try to stay in line of sight, or null if LoS
     *            should be disregarded.
     * @param impassable locations of enemies or mobile hazards/obstacles that aren't in the map as walls
     * @param allies called onlyPassable in other methods, here it also represents allies for Technique things
     * @param start the Coord the pathfinder starts at.
     * @param targets a Set of Coord, not an array of Coord or variable argument list as in other methods.
     * @return an ArrayList of Coord that represents a path to travel to get to an ideal place to use tech
     */
    public ArrayList<Coord> findTechniquePath(int moveLength, Technique tech, char[][] dungeon, LOS los,
                                           Set<Coord> impassable, Set<Coord> allies, Coord start, Set<Coord> targets) {
        if(!initialized) return null;
        tech.setMap(dungeon);
        double[][] resMap = new double[width][height];
        double[][] worthMap = new double[width][height];
        double[][] userDistanceMap = new double[width][height];
        double paidLength = 0.0;

        LinkedHashSet<Coord> friends;


        for(int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                resMap[x][y] = (physicalMap[x][y] == WALL) ? 1.0 : 0.0;
                targetMap[x][y] = null;
            }
        }

        path = new ArrayList<Coord>();
        if(targets == null || targets.size() == 0)
            return path;
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if(allies == null)
            friends = new LinkedHashSet<Coord>();
        else
        {
            friends = new LinkedHashSet<Coord>(allies);
            friends.remove(start);
        }

        resetMap();
        setGoal(start);
        userDistanceMap = scan(impassable2);
        clearGoals();
        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;

        Measurement mess = measurement;
        /*
        if(measurement == Measurement.EUCLIDEAN)
        {
            measurement = Measurement.CHEBYSHEV;
        }
        */
        scan(impassable2);
        clearGoals();

        Coord tempPt = Coord.get(0, 0);
        LinkedHashMap<Coord, ArrayList<Coord>> ideal;
        // generate an array of the single best location to attack when you are in a given cell.
        for(int x = 0; x < width; x++)
        {
            tempPt = tempPt.setX(x);
            CELL:
            for(int y = 0; y < height; y++)
            {
                tempPt = tempPt.setY(y);
                if(gradientMap[x][y] == WALL || gradientMap[x][y] == DARK || userDistanceMap[x][y] > moveLength * 2.0)
                    continue;
                if (gradientMap[x][y] >= tech.aoe.getMinRange() && gradientMap[x][y] <= tech.aoe.getMaxRange()) {
                    for (Coord tgt : targets) {
                        if (los == null || los.isReachable(resMap, x, y, tgt.x, tgt.y)) {
                            ideal = tech.idealLocations(tempPt, targets, friends);
                            // this is weird but it saves the trouble of getting the iterator and checking hasNext() .
                            for(Map.Entry<Coord, ArrayList<Coord>> ip : ideal.entrySet()) {
                                targetMap[x][y] = ip.getKey();
                                worthMap[x][y] = ip.getValue().size();
                                setGoal(x, y);
                                gradientMap[x][y] = 0;
                                break;
                            }
                            continue CELL;
                        }
                    }
                    gradientMap[x][y] = FLOOR;
                }
                else
                    gradientMap[x][y] = FLOOR;
            }
        }
        scan(impassable2);

        double currentDistance = gradientMap[start.x][start.y];
        if(currentDistance <= moveLength)
        {
            Coord[] g_arr = new Coord[goals.size()];
            g_arr = goals.keySet().toArray(g_arr);

            goals.clear();
            setGoal(start);
            scan(impassable2);
            goals.clear();
            gradientMap[start.x][start.y] = moveLength;

            for (Coord g : g_arr) {
                if (gradientMap[g.x][g.y] <= moveLength && worthMap[g.x][g.y] > 0) {
                    goals.put(g, 0.0 - worthMap[g.x][g.y]);
                }
            }
            resetMap();
           /* for(Coord g : goals.keySet())
            {
                gradientMap[g.x][g.y] = 0.0 - worthMap[g.x][g.y];
            }*/
            scan(impassable2);

        }

        measurement = mess;

        Coord currentPos = Coord.get(start.x, start.y);
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }
            if(best >= gradientMap[currentPos.x][currentPos.y])
            {
                if (friends.contains(currentPos)) {
                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findTechniquePath(moveLength, tech, dungeon, los, impassable2,
                            friends, start, targets);
                }
                break;
            }
            if (best > gradientMap[start.x][start.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            path.add(currentPos);
            paidLength += costMap[currentPos.x][currentPos.y];
            frustration++;
            if (paidLength > moveLength - 1.0) {
                if (friends.contains(currentPos)) {
                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findTechniquePath(moveLength, tech, dungeon, los, impassable2,
                            friends, start, targets);
                }
                break;
            }
//            if(gradientMap[currentPos.x][currentPos.y] == 0)
//                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }


    private double cachedLongerPaths = 1.2;
    private Set<Coord> cachedImpassable = new LinkedHashSet<Coord>();
    private Coord[] cachedFearSources;
    private double[][] cachedFleeMap;
    private int cachedSize = 1;
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed fearSources and start point, and returns a list
     * of Coord positions (using Manhattan distance) needed to get further from the closest fearSources, meant
     * for running away. The maximum length of the returned list is given by length; if moving the full
     * length of the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a fearSource overlapping one. The preferLongerPaths parameter
     * is meant to be tweaked and adjusted; higher values should make creatures prefer to escape out of
     * doorways instead of hiding in the closest corner, and a value of 1.2 should be typical for many maps.
     * The parameters preferLongerPaths, impassable, and the varargs used for fearSources will be cached, and
     * any subsequent calls that use the same values as the last values passed will avoid recalculating
     * unnecessary scans.
     *
     * @param length
     * @param preferLongerPaths Set this to 1.2 if you aren't sure; it will probably need tweaking for different maps.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param fearSources
     * @return
     */
    public ArrayList<Coord> findFleePath(int length, double preferLongerPaths, Set<Coord> impassable,
                                         Set<Coord> onlyPassable, Coord start, Coord... fearSources) {
        if (!initialized) return null;
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if (onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();
        if (fearSources == null || fearSources.length < 1) {
            path = new ArrayList<Coord>();
            return path;
        }
        if (cachedSize == 1 && preferLongerPaths == cachedLongerPaths && impassable2.equals(cachedImpassable) &&
                fearSources.equals(cachedFearSources)) {
            gradientMap = cachedFleeMap;
        }
        else {
            cachedLongerPaths = preferLongerPaths;
            cachedImpassable = new LinkedHashSet<Coord>(impassable2);
            cachedFearSources = fearSources.clone();
            cachedSize = 1;
            resetMap();
            for (Coord goal : fearSources) {
                setGoal(goal.x, goal.y);
            }
            if(goals.isEmpty())
                return path;

            scan(impassable2);

            for (int x = 0; x < gradientMap.length; x++) {
                for (int y = 0; y < gradientMap[x].length; y++) {
                    gradientMap[x][y] *= (gradientMap[x][y] >= FLOOR) ? 1.0 : (0.0 - preferLongerPaths);
                }
            }
            scan(impassable2);
            cachedFleeMap = gradientMap.clone();
        }
        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }
            if (best >= gradientMap[start.x][start.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            if(path.size() > 0) {
                Coord last = path.get(path.size() - 1);
                if (gradientMap[last.x][last.y] <= gradientMap[currentPos.x][currentPos.y])
                    break;
            }
            path.add(currentPos);
            frustration++;
            paidLength += costMap[currentPos.x][currentPos.y];
            if (paidLength > length - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findFleePath(length, preferLongerPaths, impassable2, onlyPassable, start, fearSources);
                }
                break;
            }
        }
        frustration = 0;
        goals.clear();
        return path;
    }
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to the closest reachable
     * goal. The maximum length of the returned list is given by length; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     * The parameter size refers to the side length of a square unit, such as 2 for a 2x2 unit. The
     * parameter start must refer to the minimum-x, minimum-y cell of that unit if size is &gt; 1, and
     * all positions in the returned path will refer to movement of the minimum-x, minimum-y cell.
     *
     * @param size
     * @param length
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findPathLarge(int size, int length, Set<Coord> impassable,
                                     Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;

        scan(impassable2, size);
        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }

            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);

            path.add(currentPos);
            paidLength += costMap[currentPos.x][currentPos.y];
            frustration++;
            if (paidLength > length - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findPathLarge(size, length, impassable2, onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to a goal, until preferredRange is
     * reached, or further from a goal if the preferredRange has not been met at the current distance.
     * The maximum length of the returned list is given by moveLength; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     * The parameter size refers to the side length of a square unit, such as 2 for a 2x2 unit. The
     * parameter start must refer to the minimum-x, minimum-y cell of that unit if size is &gt; 1, and
     * all positions in the returned path will refer to movement of the minimum-x, minimum-y cell.
     *
     * @param moveLength
     * @param preferredRange
     * @param los a squidgrid.LOS object if the preferredRange should try to stay in line of sight, or null if LoS
     *            should be disregarded.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findAttackPathLarge(int size, int moveLength, int preferredRange, LOS los, Set<Coord> impassable,
                                           Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        if(preferredRange < 0) preferredRange = 0;
        double[][] resMap = new double[width][height];
        if(los != null)
        {
            for(int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    resMap[x][y] = (physicalMap[x][y] == WALL) ? 1.0 : 0.0;
                }
            }
        }
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return path;

        Measurement mess = measurement;
        if(measurement == Measurement.EUCLIDEAN)
        {
            measurement = Measurement.CHEBYSHEV;
        }
        scan(impassable2, size);
        goals.clear();

        for(int x = 0; x < width; x++)
        {
            CELL:
            for(int y = 0; y < height; y++)
            {
                if(gradientMap[x][y] == WALL || gradientMap[x][y] == DARK)
                    continue;
                if (x+2 < width && y + 2 < height && gradientMap[x][y] == preferredRange && los != null) {
                    for (Coord goal : targets) {
                        if (los.isReachable(resMap, x, y, goal.x, goal.y)
                                || los.isReachable(resMap, x+1, y, goal.x, goal.y)
                                || los.isReachable(resMap, x, y+1, goal.x, goal.y)
                                || los.isReachable(resMap, x+1, y+1, goal.x, goal.y)) {
                            setGoal(x, y);
                            gradientMap[x][y] = 0;
                            continue CELL;
                        }
                    }
                    gradientMap[x][y] = FLOOR;
                }
                else
                    gradientMap[x][y] = FLOOR;
            }
        }
        measurement = mess;
        scan(impassable2, size);

        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }
            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }

            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);
            path.add(currentPos);
            frustration++;
            paidLength += costMap[currentPos.x][currentPos.y];
            if (paidLength > moveLength - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findAttackPathLarge(size, moveLength, preferredRange, los, impassable2, onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }
    /**
     * Scans the dungeon using DijkstraMap.scan with the listed goals and start point, and returns a list
     * of Coord positions (using the current measurement) needed to get closer to a goal, until a cell is reached with
     * a distance from a goal that is at least equal to minPreferredRange and no more than maxPreferredRange,
     * which may go further from a goal if the minPreferredRange has not been met at the current distance.
     * The maximum length of the returned list is given by moveLength; if moving the full length of
     * the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a goal overlapping one.
     * The parameter size refers to the side length of a square unit, such as 2 for a 2x2 unit. The
     * parameter start must refer to the minimum-x, minimum-y cell of that unit if size is &gt; 1, and
     * all positions in the returned path will refer to movement of the minimum-x, minimum-y cell.
     *
     * @param size
     * @param moveLength
     * @param minPreferredRange
     * @param maxPreferredRange
     * @param los a squidgrid.LOS object if the preferredRange should try to stay in line of sight, or null if LoS
     *            should be disregarded.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param targets
     * @return
     */
    public ArrayList<Coord> findAttackPathLarge(int size, int moveLength, int minPreferredRange, int maxPreferredRange, LOS los,
                                           Set<Coord> impassable, Set<Coord> onlyPassable, Coord start, Coord... targets) {
        if(!initialized) return null;
        if(minPreferredRange < 0) minPreferredRange = 0;
        if(maxPreferredRange < minPreferredRange) maxPreferredRange = minPreferredRange;
        double[][] resMap = new double[width][height];
        if(los != null)
        {
            for(int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    resMap[x][y] = (physicalMap[x][y] == WALL) ? 1.0 : 0.0;
                }
            }
        }
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if(onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();

        resetMap();
        for (Coord goal : targets) {
            setGoal(goal);
        }
        if(goals.isEmpty())
            return path;

        Measurement mess = measurement;
        if(measurement == Measurement.EUCLIDEAN)
        {
            measurement = Measurement.CHEBYSHEV;
        }
        scan(impassable2, size);
        goals.clear();

        for(int x = 0; x < width; x++)
        {
            CELL:
            for(int y = 0; y < height; y++)
            {
                if(gradientMap[x][y] == WALL || gradientMap[x][y] == DARK)
                    continue;
                if (x+2 < width && y + 2 < height && gradientMap[x][y] >= minPreferredRange && gradientMap[x][y] <= maxPreferredRange
                        && los != null) {
                    for (Coord goal : targets) {
                        if (los.isReachable(resMap, x, y, goal.x, goal.y)
                                || los.isReachable(resMap, x+1, y, goal.x, goal.y)
                                || los.isReachable(resMap, x, y+1, goal.x, goal.y)
                                || los.isReachable(resMap, x+1, y+1, goal.x, goal.y)) {
                            setGoal(x, y);
                            gradientMap[x][y] = 0;
                            continue CELL;
                        }
                    }
                    gradientMap[x][y] = FLOOR;
                }
                else
                    gradientMap[x][y] = FLOOR;
            }
        }
        measurement = mess;
        scan(impassable2, size);

        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }

            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }
            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);

            path.add(currentPos);
            frustration++;
            paidLength += costMap[currentPos.x][currentPos.y];
            if (paidLength > moveLength - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findAttackPathLarge(size, moveLength, minPreferredRange, maxPreferredRange, los, impassable2,
                            onlyPassable, start, targets);
                }
                break;
            }
            if(gradientMap[currentPos.x][currentPos.y] == 0)
                break;
        }
        frustration = 0;
        goals.clear();
        return path;
    }

    /**
     * Scans the dungeon using DijkstraMap.scan with the listed fearSources and start point, and returns a list
     * of Coord positions (using Manhattan distance) needed to get further from the closest fearSources, meant
     * for running away. The maximum length of the returned list is given by length; if moving the full
     * length of the list would place the mover in a position shared by one of the positions in onlyPassable
     * (which is typically filled with friendly units that can be passed through in multi-tile-
     * movement scenarios), it will recalculate a move so that it does not pass into that cell.
     * The keys in impassable should be the positions of enemies and obstacles that cannot be moved
     * through, and will be ignored if there is a fearSource overlapping one. The preferLongerPaths parameter
     * is meant to be tweaked and adjusted; higher values should make creatures prefer to escape out of
     * doorways instead of hiding in the closest corner, and a value of 1.2 should be typical for many maps.
     * The parameters size, preferLongerPaths, impassable, and the varargs used for fearSources will be cached, and
     * any subsequent calls that use the same values as the last values passed will avoid recalculating
     * unnecessary scans. Calls to findFleePath will cache as if size is 1, and may share a cache with this function.
     * The parameter size refers to the side length of a square unit, such as 2 for a 2x2 unit. The
     * parameter start must refer to the minimum-x, minimum-y cell of that unit if size is &gt; 1, and
     * all positions in the returned path will refer to movement of the minimum-x, minimum-y cell.
     *
     * @param size
     * @param length
     * @param preferLongerPaths Set this to 1.2 if you aren't sure; it will probably need tweaking for different maps.
     * @param impassable
     * @param onlyPassable
     * @param start
     * @param fearSources
     * @return
     */
    public ArrayList<Coord> findFleePathLarge(int size, int length, double preferLongerPaths, Set<Coord> impassable,
                                         Set<Coord> onlyPassable, Coord start, Coord... fearSources) {
        if (!initialized) return null;
        path = new ArrayList<Coord>();
        LinkedHashSet<Coord> impassable2;
        if(impassable == null)
            impassable2 = new LinkedHashSet<Coord>();
        else
            impassable2 = new LinkedHashSet<Coord>(impassable);

        if (onlyPassable == null)
            onlyPassable = new LinkedHashSet<Coord>();
        if (fearSources == null || fearSources.length < 1) {
            path = new ArrayList<Coord>();
            return path;
        }
        if (size == cachedSize && preferLongerPaths == cachedLongerPaths && impassable2.equals(cachedImpassable)
                && fearSources.equals(cachedFearSources)) {
            gradientMap = cachedFleeMap;
        }
        else {
            cachedLongerPaths = preferLongerPaths;
            cachedImpassable = new LinkedHashSet<Coord>(impassable2);
            cachedFearSources = fearSources.clone();
            cachedSize = size;
            resetMap();
            for (Coord goal : fearSources) {
                setGoal(goal.x, goal.y);
            }
            if(goals.isEmpty())
                return path;

            scan(impassable2, size);

            for (int x = 0; x < gradientMap.length; x++) {
                for (int y = 0; y < gradientMap[x].length; y++) {
                    gradientMap[x][y] *= (gradientMap[x][y] >= FLOOR) ? 1.0 : (0.0 - preferLongerPaths);
                }
            }
            scan(impassable2, size);
            cachedFleeMap = gradientMap.clone();
        }
        Coord currentPos = start;
        double paidLength = 0.0;
        while (true) {
            if (frustration > 500) {
                path = new ArrayList<Coord>();
                break;
            }

            double best = gradientMap[currentPos.x][currentPos.y];
            Direction[] dirs0 = rng.shuffle((measurement == Measurement.MANHATTAN)
                    ? Direction.CARDINALS : Direction.OUTWARDS);
            Direction[] dirs = Arrays.copyOf(dirs0, dirs0.length + 1);
            dirs[dirs0.length] = Direction.NONE;
            int choice = rng.nextInt(dirs.length);

            for (int d = 0; d < dirs.length; d++) {
                Coord pt = Coord.get(currentPos.x + dirs[d].deltaX, currentPos.y + dirs[d].deltaY);
                if (gradientMap[pt.x][pt.y] < best) {
                    if (dirs[choice] == Direction.NONE || !path.contains(pt)) {
                        best = gradientMap[pt.x][pt.y];
                        choice = d;
                    }
                }
            }
            if (best >= gradientMap[currentPos.x][currentPos.y] || physicalMap[currentPos.x + dirs[choice].deltaX][currentPos.y + dirs[choice].deltaY] > FLOOR) {
                path = new ArrayList<Coord>();
                break;
            }
            currentPos = currentPos.translate(dirs[choice].deltaX, dirs[choice].deltaY);

            if(path.size() > 0) {
                Coord last = path.get(path.size() - 1);
                if (gradientMap[last.x][last.y] <= gradientMap[currentPos.x][currentPos.y])
                    break;
            }
            path.add(currentPos);
            frustration++;
            paidLength += costMap[currentPos.x][currentPos.y];
            if (paidLength > length - 1.0) {
                if (onlyPassable.contains(currentPos)) {

                    closed.put(currentPos, WALL);
                    impassable2.add(currentPos);
                    return findFleePathLarge(size, length, preferLongerPaths, impassable2, onlyPassable, start, fearSources);
                }
                break;
            }
        }
        frustration = 0;
        goals.clear();
        return path;
    }

    /**
     * A simple limited flood-fill that returns a LinkedHashMap of Coord keys to the Double values in the DijkstraMap, only
     * calculating out to a number of steps determined by limit. This can be useful if you need many flood-fills and
     * don't need a large area for each, or if you want to have an effect spread to a certain number of cells away.
     * @param radius the number of steps to take outward from each starting position.
     * @param starts a vararg group of Points to step outward from; this often will only need to be one Coord.
     * @return A LinkedHashMap of Coord keys to Double values; the starts are included in this with the value 0.0.
     */
    public LinkedHashMap<Coord, Double> floodFill(int radius, Coord... starts) {
        if(!initialized) return null;
        LinkedHashMap<Coord, Double> fill = new LinkedHashMap<Coord, Double>();

        resetMap();
        for (Coord goal : starts) {
            setGoal(goal.x, goal.y);
        }
        if(goals.isEmpty())
            return fill;

        partialScan(radius, null);
        double temp;
        for(int x = 1; x < width - 1; x++)
        {
            for(int y = 1; y < height - 1; y++)
            {
                temp = gradientMap[x][y];
                if(temp < FLOOR)
                {
                    fill.put(Coord.get(x, y), temp);
                }
            }
        }
        goals.clear();
        return fill;
    }

    private static final double root2 = Math.sqrt(2.0);
    private double heuristic(Direction target) {
        switch (measurement) {
            case MANHATTAN:
            case CHEBYSHEV:
                return 1.0;
            case EUCLIDEAN:
                switch (target) {
                    case DOWN_LEFT:
                    case DOWN_RIGHT:
                    case UP_LEFT:
                    case UP_RIGHT:
                        return root2;
                    default:
                        return  1.0;
                }
        }
        return 1.0;
    }
}
