/**
 * Move Data Base
 */
package frech;

/**
 * @author Ingo
 * 
 * <p>Functions for setting up tables that can later be used to find moves fast.</p>
 * 
 * <p>There is no table for Queen moves, we simply take the sum of Bishop and Rook moves.</p>
 * 
 * <p>The methods here don't take into account the general rule that the target field must not
 * have a figure of the same color. For example, a Bishop on c1 will always be able to 
 * "move" to b2 and d2. This allows generation of possible moves as well as detection of coverage.</p>
 * 
 * <p>For Bishop/Rook, we return a bitmap of the fields that need to be free for the move to be
 * valid. Hence, 0 means the move is possible, and -1 means it is not possible (unless the board was completly empty).</p> 
 *
 */
public class MDB {
	//                  Geometry
	//      8        7        6       5         4        3       2        1
	//  hgfedcba hgfedcba hgfedcba hgfedcba hgfedcba hgfedcba hgfedcba hgfedcba
	//  10101010 01010101 11001100 00110011 01100110 11001100 00110011 01100110
	//
	// A bit in a bitset corresponds to the index (0..63) that is equal to the number of
	// lower significance bits.
	// Example: e2 = 1000000000000(2) = 0x1000L = index 12
	//          index 12 -> 1L << 12  = 0x1000L
	// (index >>> 3) + 1 is the row number
	// chr((index & 7) + ord 'a') is the column name
	
	public final static long oben   = 0xff00000000000000L;
	public final static long rechts = 0x8080808080808080L;
	public final static long links  = 0x0101010101010101L;
	public final static long unten  = 0x00000000000000ffL;
	public final static int  EAST = 1;
	public final static int  WEST = -1;
	public final static int  SOUTH = -8;
	public final static int  NORTH = 8;
	public final static int  NE = NORTH+EAST;
	public final static int  SE = SOUTH+EAST;
	public final static int  SW = SOUTH+WEST;
	public final static int  NW = NORTH+WEST;
	
	/***
	 * <p>Moves for Bishops</p>
	 * <p>This table is indexed with <code>(from&lt;&lt;6)+to</code>, where from and to are
	 * field numbers 0..63</p>
	 * <p>Each entry is a bit set that indicates which fields must be empty for making the move legal.</p>
	 * 
	 */
	private static long bishopFromTo[];
	private static long rookFromTo[];
	
	/***
	 * <p>Set of fields that can in principle be reached by a Bishop from a certain field.</p>
	 * <p>This is indexed by a field number 0..63<br>
	 * The value can be used to find relevant entries in {@link MDB.bishopFromTo} and finally check the free
	 * fields in the current position.
	 */
	private static long bishopTo[];
	private static long rookTo[];
	private static long knightTo[];
	private static long kingTo[];
	
	/**
	 * 
	 * @param from index (0..63) of source field
	 * @param to index (0..63) of target field
	 * @return a bit set indicating the fields that must be empty for making this a valid move, 
	 *         or -1L if this is never valid. Note that an empty set (0L) indicates the move is always valid.
	 */
	public static long canBishop(int from, int to) {
		return bishopFromTo[(from<<6)+to];
	}
	
	/**
	 * @see canBishop
	 */
	public static long canRook(int from, int to) {
		return rookFromTo[(from<<6)+to];
	}
	
	/**
	 * 
	 * @see bishopTo
	 */
	public static long bishopTargets(int from) {
		return bishopTo[from];
	}
	
	/**
	 * 
	 * @see rookTo
	 */
	public static long rookTargets(int from) {
		return rookTo[from];
	}
	
	/**
	 * @see kingTo
	 */
	public static long kingTargets(int from) {
		return kingTo[from];
	}
	
	/**
	 * 
	 * @see knightTo
	 */
	public static long knightTargets(int from) {
		return knightTo[from];
	}
	
	public static int setToIndex(long singleton) {
		return Long.numberOfTrailingZeros(singleton);
	}
	
	/**
	 * @param fld - a singleton set that denotes the field in question
	 * @param direction - one of NORTH, EAST, SOUTH, WEST, NE, NW, SE, SW
	 * @return the field that is one step from the given field in a given direction,
	 *         if there is such a field, otherwise, nonsense results.
	 * @see canGo
	 */
	public static long goTowards(long fld, int direction) {
		return direction < 0 ? fld >>> (-direction) : fld << direction; 
	}
	
	/***
	 * tell if we can go into direction from some field
	 */
	public static boolean canGo(long from, int direction) {
		switch (direction) {
		case EAST: 	return (from & rechts) == 0;
		case WEST: 	return (from & links)  == 0;
		case NORTH:	return (from & oben)  == 0;
		case SOUTH:	return (from & unten) == 0;
		case NE:	return (from & (oben  | rechts)) == 0;
		case SE:	return (from & (unten | rechts)) == 0;
		case NW:	return (from & (oben  | links)) == 0;
		case SW:	return (from & (unten | links)) == 0;
		}
		return false;
	}
	
	/***
	 * tell if we can go 2 steps in the direction
	 */
	public static boolean canGo2(long from, int direction) {
		return canGo(from, direction) 
			&& canGo(goTowards(from, direction), direction);
	}
	
	public static void genBishop() {
		bishopFromTo = new long[64*64];
		bishopTo     = new long[64];
		final int[] directions = new int[] {NE, SE, SW, NW};
		
		// mark all moves illegal
		for (int i=0; i<bishopFromTo.length; i++) bishopFromTo[i] = -1L; 
		
		long from = 1;
		
		for (from = 1L; from != 0L; from <<= 1) {    	// for all fields
			long mask = 0L;
			for (int d : directions) {					// for all directions
				long to = from;
				mask = 0;
				while (canGo(to, d)) {
					to = goTowards(to, d);
					bishopTo[setToIndex(from)] |= to;		// can go there
					bishopFromTo[(setToIndex(from)<<6) + setToIndex(to)] = mask;
					mask |= to;
				}	
			}
		}
	}
	
	public static void genRook() {
		rookFromTo = new long[64*64];
		rookTo     = new long[64];
		final int[] directions = new int[] {NORTH, SOUTH, EAST, WEST};
		
		// mark all moves illegal
		for (int i=0; i<rookFromTo.length; i++) rookFromTo[i] = -1L; 
		
		long from = 1;
		
		for (from = 1L; from != 0L; from <<= 1) {    	// for all fields
			long mask = 0L;
			for (int d : directions) {					// for all directions
				long to = from;
				mask = 0;
				while (canGo(to, d)) {
					to = goTowards(to, d);
					rookTo[setToIndex(from)] |= to;		// can go there
					rookFromTo[(setToIndex(from)<<6) + setToIndex(to)] = mask;
					mask |= to;
				}	
			}
		}
	}
	
	public static void genKnight() {
		knightTo = new long[64];
		final int[] directions = new int[] {NORTH, SOUTH, EAST, WEST};
		final int[] schräg1    = new int[] {NE,    SE,    NE,   NW};
		final int[] schräg2    = new int[] {NW,    SW,    SE,   SW};
		
		for (long from = 1L; from != 0; from <<= 1) {
			for (int i=0; i < 4; i++) {
				int d1 = directions[i];
				if (canGo(from, d1)) {
					long to1 = goTowards(from, d1);
					int d2 = schräg1[i];
					int d3 = schräg2[i];
					if (canGo(to1,d2)) {
						long to2 = goTowards(to1, d2);
						knightTo[setToIndex(from)] |= to2;
					}
					if (canGo(to1,d3)) {
						long to2 = goTowards(to1, d3);
						knightTo[setToIndex(from)] |= to2;
					}
				}
			}
		}
	}
	
	public static void genKing() {
		kingTo = new long[64];
		final int[] directions = new int[] {NORTH, NE, EAST, SE, SOUTH, SW, WEST, NW };
		for (long from = 1L; from != 0; from <<= 1) {
			for (int d:directions) {
				if (canGo(from, d)) {
					long to = goTowards(from, d);
					kingTo[setToIndex(from)] |= to;
				}
			}
		}
	}
	
	public static String showf(int field) {
		return String.format("%c%d", 'a' + (field & 7), 1 + (field >> 3));
		
	}
	
	public static String showSet(long x) {
		StringBuilder sb = new StringBuilder("[");
		while (x != 0) {
			int f = setToIndex(x);
			x ^= 1L << f;
			sb.append(showf(f));
			if (x != 0) sb.append(",");
		}
		sb.append("]");
		return sb.toString();
	}
	
	static {
		genBishop();
		genRook();
		genKnight();
		genKing();
	}
	
	public static void main(String[] args) {
		
		int from, to;
		for (from = 0; from < 64; from++) {
			if (rookTargets(from) == 0) continue;
			System.out.print(showf(from) + ": ");
			for (to = 0; to < 64; to++) {
				long mask = canRook(from, to);
				if (mask == -1L) continue;
				System.out.print(showf(to) + showSet(mask) + "  ");
			}
			System.out.println();
		}
	}
}