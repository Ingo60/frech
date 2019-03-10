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
	private static long whitePawnFromTo[];
	private static long blackPawnFromTo[];
	
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
	private static long whitePawnTo[];
	private static long blackPawnTo[];
	
	/***
	 * <p>The inverse of whitePawnTo/blackPawnTo: if there is a black/white pawn on any of the
	 * indicated fields, then the given field is attacked.</p>
	 * 
	 *  <p>We need this only for pawns, since the moves of all other pieces are symmetric, that is,
	 *  for example, the set of fields an index can be attacked from with a bishop is the same as
	 *  the set of fields a bishop on index can go to.</p>
	 */
	private static long whitePawnFrom[];
	private static long blackPawnFrom[];
	
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
	 * @see canBishop
	 */
	public static long canWhitePawn(int from, int to) {
		return whitePawnFromTo[(from<<6)+to];
	}

	/**
	 * @see canBishop
	 */
	public static long canBlackPawn(int from, int to) {
		return blackPawnFromTo[(from<<6)+to];
	}

	/**
	 * @see whitePawnTo
	 */
	public static long whitePawnTargets(int from) {
		return whitePawnTo[from];
	}
	
	/**
	 * @see blackPawnTo
	 */
	public static long blackPawnTargets(int from) {
		return blackPawnTo[from];
	}
	
	/**
	 * @see blackPawnFrom
	 */
	public static long targetOfBlackPawns(int to) {
		return blackPawnFrom[to];
	}
	
	/**
	 * @see whitePawnFrom
	 */
	public static long targetOfWhitePawns(int to) {
		return whitePawnFrom[to];
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
	
	public static void genPawn() {
		whitePawnTo = new long[64];
		whitePawnFrom = new long[64];
		whitePawnFromTo = new long[64*64];
		blackPawnTo = new long[64];
		blackPawnFrom = new long[64];
		blackPawnFromTo = new long[64*64];

		// mark all moves illegal
		for (int i=0; i<whitePawnFromTo.length; i++) whitePawnFromTo[i] = blackPawnFromTo[i] = -1L;
		for (int i=0; i<whitePawnTo.length;i++)      whitePawnTo[i]     = blackPawnTo[i]     = 0L;
		for (int i=0; i<whitePawnTo.length;i++)      whitePawnFrom[i]   = blackPawnFrom[i]   = 0L;
		long from = 0L;
		
		for (from = 0x100L; from < 0x0100000000000000L; from <<= 1) {
			long mask = 0L;
			long to   = 0L;
			if (canGo(from, NORTH)) {
				to = goTowards(from, NORTH);
				whitePawnTo[setToIndex(from)] |= to;
				whitePawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = to;
				if ((from & 0xff00L) != 0 && canGo(to, NORTH)) {
					// A2..H2 second rank
					mask = to;
					to = goTowards(to, NORTH);
					whitePawnTo[setToIndex(from)] |= to;
					whitePawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = to | mask;
				}
			}
			if (canGo(from, SOUTH)) {
				to = goTowards(from, SOUTH);
				blackPawnTo[setToIndex(from)] |= to;
				blackPawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = to;
				if ((from & 0x00ff000000000000L) != 0 && canGo(to, SOUTH)) {
					// A7..H7 seventh rank
					mask = to;
					to = goTowards(to, SOUTH);
					blackPawnTo[setToIndex(from)] |= to;
					blackPawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = to | mask;
				}
			}
			if (canGo(from, NE)) {
				to = goTowards(from, NE);
				whitePawnTo[setToIndex(from)] |= to;
				whitePawnFrom[setToIndex(to)] |= from;
				whitePawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = 0L;
			}
			if (canGo(from, NW)) {
				to = goTowards(from, NW);
				whitePawnTo[setToIndex(from)] |= to;
				whitePawnFrom[setToIndex(to)] |= from;
				whitePawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = 0L;
			}
			if (canGo(from, SE)) {
				to = goTowards(from, SE);
				blackPawnTo[setToIndex(from)] |= to;
				blackPawnFrom[setToIndex(to)] |= from;
				blackPawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = 0L;
			}
			if (canGo(from, SW)) {
				to = goTowards(from, SW);
				blackPawnTo[setToIndex(from)] |= to;
				blackPawnFrom[setToIndex(to)] |= from;
				blackPawnFromTo[(setToIndex(from)<<6) + setToIndex(to)] = 0L;
			}
		}
	}
	
	public static void genBishop() {
		bishopFromTo = new long[64*64];
		bishopTo     = new long[64];
		final int[] directions = new int[] {NE, SE, SW, NW};
		
		// mark all moves illegal
		for (int i=0; i<bishopFromTo.length; i++) bishopFromTo[i] = -1L; 
		
		long from = 0L;
		
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
		genPawn();
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

	public static volatile boolean stopThinking = false;
	
	public static void finishThinking() { stopThinking = true; }
	public static void beginThinking()  { stopThinking = false; } 

	/**
	 * The index in the zrandoms table is pxxxffffff
	 * 
	 * Computes the zobrist constant for a certain piece (1..6) of a certain player on a certain square.
	 * To encode flag bits use BLACK, EMPTY and index of a flags bit.
	 * 
	 * @param player BLACK or WHITE
	 * @param piece  PAWN … KING
	 * @param index  A1 … H8
	 * @return
	 */
	public static long ppfZobrist(int player, int piece, int index) {
		return zrandoms[((player*6 + piece)<<6) + index];
	}

	/**
	 * Convenience to get zobrist keys for flag bits (i.e. en-passant and castling information) 
	 */
	public static long flagZobrist(int index) {
		return ppfZobrist(0,0,index);
	}
	
	/**
	 * 1000 random long numbers for computation of Zobrist hash keys
	 * We need only 832, but hey ....
	 */
	
	private final static long[] zrandoms = new long[] {
		0x29e48473df053401L, 0x436328cac0eb14ffL, 0x94df472ce2bfc808L, 0xfae82f66506c5e5bL,
		0xdb9533eb28d7d1caL, 0xeed84e170d796a72L, 0x5906d39eb751b57aL, 0x40e99acbffd458c1L,
		0x273be4babd76ac77L, 0xdf2c0fb73f664547L, 0x4c9c77ab65735ec3L, 0x54a0eac3783fc85fL,
		0xf843100082dc36d9L, 0x5df28348d96f3844L, 0x988303a7324925abL, 0xea48dd88e6d354e2L,
		0xdc814d4f1bc3a8caL, 0x823bdac94369523fL, 0x3118eac3c19b802dL, 0x0aad812db43efc0cL,
		0xd81d7eda97cdc210L, 0x0ab05fa14d5d9f9aL, 0xd7f1225942dce6a8L, 0x6cc0e8dc3dd257e5L,
		0x5206013085c57895L, 0x344628d0b9b713e0L, 0x8cf903a4bfba0ecaL, 0xad8fcf2eae49d993L,
		0x84c5fa820ecc543cL, 0x59839488223a2ddaL, 0xc7405eccfef2cb09L, 0xbbb5bb5a13055261L,
		0xa7c4d3a6f311ae25L, 0xf26a9eab70b34a7fL, 0x50209ba6d3f55129L, 0x19ef1e8a0357c208L,
		0x0e5702e244506f88L, 0xb81289345c249da4L, 0x60df65c51999e930L, 0x72b59da413cc4425L,
		0xccef8c5484b5323eL, 0x650bf04724e3966cL, 0x75de44651dc00dd7L, 0xc2d186360946f37aL,
		0x8b06b62560e44fa5L, 0xa54e78d6131bf912L, 0xcba4e9b9cbb86d34L, 0x96e6bfa59c99f5ceL,
		0x1b7dd30651a34294L, 0xd844d6ae8de36fc2L, 0xd6306da1bf314951L, 0xfe07c04083519a3bL,
		0xa6e69bbe358d4af2L, 0x7b054225cba9d98eL, 0x4b9a4644c8c1e6caL, 0x5f5dab707fc7477aL,
		0xa2650c332ea6fc4fL, 0x596117c0085747baL, 0xf0704670784724edL, 0x53c9c56fe2e94379L,
		0x4c1d5e71e14b10f0L, 0x9ecc599323b16521L, 0x578c5ac788ff9225L, 0x4eee523c3dc95f76L,
		0xd62526f8e8245124L, 0x78761b06e4682974L, 0x7ca9bd890e444134L, 0x1dd2e6dd4cc41305L,
		0x21873db0e031bf7cL, 0xafa3a4cca3e3e8adL, 0x71c2282d5df16e4dL, 0xf057459586cdeb8cL,
		0x4f79a3b5f808a652L, 0x51b58d7c5a5d85dbL, 0x263fc9ffe98bc388L, 0x68cba7e7bffd7d06L,
		0x3f8c984f953728c1L, 0x1dfe05758e716983L, 0x49ec0fdc97128476L, 0x1752f9fc30c909eaL,
		0x86f34ffc8a8c3c98L, 0x14d36176d6df20f4L, 0xc90e45c5ec58c801L, 0xfb6435bb262363b1L,
		0xa4e6a681cf54a942L, 0x6f5c8de1616d1fa8L, 0xf0937bda27277c5bL, 0x191dd9049d45f398L,
		0xdcaf9064ed42e39dL, 0x1254fa28500d2ac4L, 0xf8ea0b52dd2fbbb3L, 0x5ecba59731174e03L,
		0x66b7fe6ee2939c99L, 0xe320e2b7bdd0a576L, 0xdf2b8125622b9e2aL, 0x5b13fa01203dfc61L,
		0xc02950a28987230cL, 0x47541d397cca32d9L, 0xecdde5ca75fd1859L, 0x5db52acea21e5587L,
		0xec0081ee8cdbab48L, 0x4a44f91ae4fca277L, 0xeaa99120dc447512L, 0x98b522685ea42db5L,
		0x1bd2bcc611a28da6L, 0x4a5b90a509cba427L, 0xfd9d90a27c84ada8L, 0xf46391d4467fc1acL,
		0x85186c2f86d2dbd0L, 0x8e81bf6fef2606e2L, 0xc2fb30f85aabc069L, 0x0a03a3c3ad2c4354L,
		0x6fd052a4b92e1e07L, 0x0f61a0e169f0d9baL, 0x8b3f31e062bdfe62L, 0x24b0ab1ac04810d8L,
		0x5fd4c183dad5a310L, 0xd6228b34c0288b2dL, 0xe384b72d9b2e48b4L, 0xf49a49ae9a3fc516L,
		0xa440b1c9cafe0cb6L, 0xbb7fa02ec682f66cL, 0x898f1cbe177a411dL, 0x41699946c98e7c13L,
		0x19edfeb465cf92d9L, 0x77a97f2c8a83bcb0L, 0x982faab2298f5654L, 0xc2f1bd86990c1194L,
		0x1ce17fd6e56ffd05L, 0x51c2cabc9c358b9dL, 0x07b70d2b8129daacL, 0xd369d7aab22e6bfeL,
		0xda66aab2f3226443L, 0x8d816199cb5971bbL, 0xf0d56ce2021b2118L, 0x4c4f866cb372a69dL,
		0xec8ed21f3e20b3b6L, 0x74557cd959772cfdL, 0xb5604b4c2f792b2aL, 0xcbaca2d0e25d248eL,
		0x0d72a67696e7ce0dL, 0x56f355d9a52ddc40L, 0x10918bb594d7260dL, 0xe79be59e1c6850d2L,
		0x2dc8da657c20020dL, 0x1bdfbb28a1735843L, 0xdb3c5b0e4e38a0d0L, 0xe5c8da62dbd72b71L,
		0xe1b50fd75ad0628fL, 0xe4387eb709964157L, 0xf5e631f7ba745768L, 0x8143b8f96046aa74L,
		0x91bd3e5fae641abfL, 0x59709188f37af0b5L, 0x40b55aadb541deebL, 0x0f54388ff13c141bL,
		0xd4c1d1a98da0695eL, 0x80c359ef7f100f59L, 0x506977c5eaae6fd8L, 0x4b3577825ca959d0L,
		0x58d459abca8e37e6L, 0xd9246655d94e807cL, 0xa768c63f09664a79L, 0x7ffaf09bb052290aL,
		0x318acefd90ae1182L, 0x2ca59ef8faa5fe5bL, 0x8c9ce9c5a8665affL, 0x71654d7f6c8fa7fdL,
		0xa16841be9190083cL, 0x07942aa92d981f8dL, 0x7acd5d4b2e6fa5caL, 0x733f241ce1eda0fbL,
		0xce1e7834d0478e61L, 0xfbb36018446f6ffbL, 0x06833e9341e44057L, 0xba4d0fbb3c565020L,
		0x1f276e52d1db2575L, 0x3a53b09f35143876L, 0xf34cce64e92d5fdaL, 0xa779ed5acb51847fL,
		0x6831f101eb7404d5L, 0xe3e14d3ade63d182L, 0x664f799ecf8149d5L, 0x7f16600301153c21L,
		0x31389f8c544a1494L, 0x13aebdd5bc9cea06L, 0xdf3df8859f7cf60cL, 0x80c08a7e655958b7L,
		0x48486659f5680b7eL, 0xae3984e8a66dbfadL, 0xcdd81c7b692d39b6L, 0xc359beba9cba845dL,
		0x7b4885a61d9ddb8bL, 0x5345d9d6a00a1711L, 0x3df0c05271e9256fL, 0xeae0ee8bd17f4447L,
		0x00a2689cd7bc7c01L, 0x17061db79ab93ebcL, 0x31ec6584b0ee49f7L, 0x626831ad8039bbb4L,
		0xc6da110d8162fc8bL, 0xbe3383fb2e988462L, 0x358e89f4a0a8cf33L, 0x32d2a34af6bbd749L,
		0x6b2ea16bd8859b6fL, 0xabd4b6541a936dc4L, 0x1b97cbd6f46ccb9aL, 0xaed99fe2765e36d0L,
		0x719deb3c8a753615L, 0xd34672a60ae995fcL, 0xb3ebbd9a12f95e3dL, 0x3b45599c6b8abeddL,
		0xbc0ec9423d8f067bL, 0x9279912c716ca9fbL, 0x644cb595426150a3L, 0x7b99d969dbe85d6dL,
		0x2dcea86bf4ea3c87L, 0x32d787c3529b5ed4L, 0x167d5309122b0957L, 0xd996c0f64a481fceL,
		0x0ec43720e7ff8fc9L, 0x87df511b7c188bf8L, 0xa27ef8b1a3897ddfL, 0xff60a2d6f5a419c8L,
		0xe03526ee6d8f0588L, 0x004c23b8aaeb88beL, 0x7c44ee6af162cc82L, 0x00c023a9064bac0dL,
		0x2e91737d9ec852d6L, 0xef63d90e138da0cbL, 0x0fe12bc9e645df46L, 0xc4887070644d8d36L,
		0xd782fc5f61d03f3aL, 0x219dd6d7d4bcae9dL, 0x2ebd9c611d46ef0aL, 0xa287aa0cae28b107L,
		0x00c48918c071d4e3L, 0xebdeae020cf3d8c9L, 0x17a51fe4cbeea648L, 0xbc1aaa2883f8ce49L,
		0x316d4da21ea5b520L, 0xe412527ab0517ddeL, 0x87642677c39e2a69L, 0x428e32219d044ffbL,
		0x4e70e7496ce920e4L, 0xbd52bd88f4bed7a2L, 0xfc252256ed5a1826L, 0xbf0ce2048aa99c1bL,
		0x5c2405727af2a1cdL, 0xca1f7dcab298a34aL, 0xdc740b991272c72cL, 0x661e4a84ecf38f0aL,
		0x39fb8e8a50164985L, 0xca15345fb0a8fb92L, 0xea58086d25e31fd5L, 0xdd0423f095ef2b43L,
		0xea026270d55dbffaL, 0xd33059e8bbc44382L, 0x9f0f9be2db38ad9fL, 0x5d65e0996ab165a1L,
		0x25ecb64958372dc9L, 0x5d3a8c56ff24478eL, 0xf610999318cae412L, 0x001eb72b63bdb74eL,
		0xa8d3ecc33c6b99faL, 0x09fcfb7f7d47ec2aL, 0x2f716b94412e21a5L, 0x15b686230b27e9cfL,
		0xdb4864c0bb3b379dL, 0x45bd96ca425f3922L, 0x83e7677eb0060d98L, 0xa23fb8ff99842ed0L,
		0xba4db85a7d67f014L, 0xc6c47829550e8adaL, 0x4cd313a6a47faf8aL, 0x11a417061d428454L,
		0xbee08ca7ce7cf4e4L, 0xba7f6d2d65135612L, 0x47aa715a615c3883L, 0x4559cfeb5edf5541L,
		0x932d994715257ed2L, 0x58d082031363adc5L, 0xb2fc6ddd5ead9986L, 0x2b7d86a49d86d228L,
		0x4f1fa3e5c081070eL, 0x93a603a01b97d706L, 0xbcda84f4204b3dedL, 0x68894d74614e422dL,
		0x8f8bcdbcced698b1L, 0x96b5781b38ab08c6L, 0xa0b9b00118797522L, 0xda00443adbe6612dL,
		0xf07a3bc381a8e34cL, 0xb9625fc84d340e93L, 0xce9aa2ae679c9ebeL, 0xaab50c21540f1b27L,
		0x9562f2920ae9f250L, 0xe4e9bca9bfdec46eL, 0x70317f7bf7aa309cL, 0xc5a848acc773dd83L,
		0x28c0434fb1a6a683L, 0x7873d626ba4eabdbL, 0xfd7c2616ec18edcfL, 0x8b41cf60f1538a4fL,
		0xae7fb7057fe0fe79L, 0x6dd2280dba5ee0d3L, 0x9233597afb4c224fL, 0x411f52ce62e087f7L,
		0x7eeb4b08853ff762L, 0x5c9770fb147a7365L, 0x94aa0ebff6b8f547L, 0x6826b97bca89f322L,
		0xae0f9f2413c0a2f8L, 0x29dd6a4ec840caccL, 0x0dfacdc57f845746L, 0x9c8f8b60735ac92bL,
		0x75254d1fed9d6239L, 0x3f14c7b2579135edL, 0xf856504dbb1a657cL, 0xec17da67f99a112bL,
		0xe541f47cb948b097L, 0xf37278cc5525a967L, 0x0f9d52c12af77fb1L, 0x159fa33bd68d89d7L,
		0x5c6502c55c48586eL, 0xb6864b3cdbc88973L, 0x9acd0ec907afadd5L, 0x824c76cdcecba9c3L,
		0xdeae54a53d5c0944L, 0x0660d9233894495cL, 0x3edffee31a6ff304L, 0xae6050befff942ffL,
		0xf86b5641907322c2L, 0xe7dae768a8d7c6ccL, 0xd31138f2728eafc4L, 0xfb615ab02b88e149L,
		0xafb0e4841b4c33d0L, 0xb257422112f94f00L, 0x2dff94f8667f90e6L, 0x55a79d3f39cd18d8L,
		0x5a736ee3dc47669fL, 0xafb086cee3238f5dL, 0x050b5a68403d48a0L, 0x5640526d0a6b3c11L,
		0xc531f0a23f8d369cL, 0x9fbf2e0b809cbf43L, 0x2d1ad5926c720ce1L, 0x6d92973305b06235L,
		0xb3909bb30ea5b961L, 0x4e172150350dcceeL, 0x066201e17ab70826L, 0x4eb22f6263f6922fL,
		0x05aa68036bbc0470L, 0x60ef56c7326e8658L, 0x2439ea78c0ccf875L, 0x1679d054aadd9fb4L,
		0x0015b7bbd4e17649L, 0x930dc11581b50f8dL, 0xe580d20db1026a80L, 0xa9195c0437ffd616L,
		0x34b19e06cf8b2505L, 0x0e86788d29dad927L, 0xdbb4e8a8857a455cL, 0x11a7c03ff35f858eL,
		0x2010ea6eb4912f6bL, 0xae19e8dc33afc0baL, 0xeca53b6d7d2373e2L, 0x7d43006cf118d379L,
		0x2ccb81b6cc5ce500L, 0xa518737309d86dabL, 0x5b43df34466c6b9aL, 0x7a9cfebe6fc74b17L,
		0xcc4446e0b71a823dL, 0x3edeae74d91eebc5L, 0xd9fb8110f8694292L, 0x91b036ad2c33caa3L,
		0x9109e68a25c9b0c1L, 0xf8a67cfcb9cd0a6eL, 0x84a73cbce230a1d9L, 0xeff65ea87e48ce7dL,
		0x9ad9addfabe1b08aL, 0xb2ee357c1ec0179aL, 0xd02b3265dee64202L, 0x96b7971f07c0347eL,
		0x11551a4baf8bb082L, 0x2e48b6e112d3497aL, 0x67fcfe9f23310af9L, 0x68b80723aa626868L,
		0xbdd6931701bd027cL, 0x2dd541256e65113cL, 0xa7174c3d1dc7a114L, 0xb7caac2195a7a622L,
		0xad010a52591f03d9L, 0x492570fd8364c25bL, 0x35a988d189a40369L, 0x2d1c49e2fb6f2350L,
		0x69a930b2abf15211L, 0x6590b16bd12ff633L, 0x7b09ad9b1274da47L, 0xe0b002f3a6861999L,
		0x5ca122115da92535L, 0x987779077510e8dbL, 0x5089ae703bfb7217L, 0x75d1e0790b9e60b2L,
		0x6c17483b4c21337bL, 0x6b705013f35e640cL, 0xe027075deebcd1acL, 0x4e41ea16d97baf2eL,
		0xbaba0dd8ac5a1fc9L, 0xc2594722b6bbde56L, 0xaf63e777e72a3e19L, 0x66077676cbaa9916L,
		0xa53d5d27cf627671L, 0xa4cb570f792399e7L, 0xaf0353c513dd4e79L, 0x8b59fa80cb20ee88L,
		0x591ade5ca8a73d0fL, 0x1eabc67797341ed2L, 0xeab570e6ad930bb4L, 0x6c722f6f656ef7e5L,
		0x62f2845c3528358fL, 0x5d0eee5c743800c6L, 0x31e35097f72eac38L, 0x9366538a7823965fL,
		0x4946cce6676d73bbL, 0xc13ecda1eb3353cdL, 0x49c262ac04c4625fL, 0x8ead783027e9cbf2L,
		0x3ec2f800eaf8d363L, 0xbf3021015eda9021L, 0x128d569d290f7187L, 0x94b4f7ee447d4f5dL,
		0x2332159053cd9e08L, 0xc5954c233588cddcL, 0x7a31c83ccbb92dc6L, 0x55f7becaa13395bdL,
		0x57ab6df03fac711bL, 0xf08590614c6ff374L, 0xda5998e17a53b268L, 0x5fe308e6e6dc6f92L,
		0x89adc19f2dc53773L, 0xd083da63939f245bL, 0x45227cca7a9d2d06L, 0xad994a04b48a9b6cL,
		0xab9ece082967b870L, 0xfc93f18fb0beaddeL, 0x287fe100753ac213L, 0xb5d4594c6f718c3fL,
		0x7374f98e99a3e691L, 0xbf50aea45d44c7c5L, 0x1f24689ec400357aL, 0x3b62e83ab3d15bfaL,
		0x7fceb4e49424dcadL, 0x7f54a2805ad27d56L, 0x7fcfa2d7ba6d7904L, 0x5049e4518245a357L,
		0x07b075d0aac41f50L, 0x1936073479af2084L, 0xa4325aad3ba96109L, 0xb1b0a87decb20c4fL,
		0xa5f731128e7757aaL, 0x4db24a1fdfd4c1ffL, 0x02b35e5f628c8effL, 0x6e8fe805b56dfdd9L,
		0x631a5baafa316423L, 0xc7cf050f2b5dc602L, 0x7e005b57b1c3709cL, 0x3cea7f395ec7d4dcL,
		0xa6506a794debc6f9L, 0xc1c3771cfa955196L, 0x2bb8efb672830378L, 0x256717c30da67f2cL,
		0x16b36229b783a098L, 0x4ced28cf3e735396L, 0x88900ec29059761cL, 0x728e1af9dc380206L,
		0xeae45c1da57ab401L, 0x87a77fc2e3d7ebd7L, 0x8af59908f2f1aa39L, 0x4ba0b1ee234e8e9aL,
		0x4608ca5b37afee89L, 0xe3ad6872fe087cceL, 0x7844e6326e36b828L, 0x3ab10e8034ce0456L,
		0x9cf5a7e624b0774bL, 0xa91068ccf81ca01fL, 0xf67f6545cb5adba0L, 0xc931ae22d5a83bb0L,
		0x9e1bc2893c7d5590L, 0x74b639092dda2ca3L, 0xa8223d6711312a7aL, 0x346948b598bf95aeL,
		0xd3226190f16e703cL, 0x66898ccc64baf1c1L, 0x6695a0414693ca9aL, 0xb87ee26fd1514ff0L,
		0x59665d1f7c542fd3L, 0x30a3a40ad0acfd04L, 0x955bd1dff7623d00L, 0x554ea5e991201b1fL,
		0x1522a95590a01045L, 0xc4975c893df2ed6aL, 0xdb71615e3e702f4aL, 0xaeff288ea60c0188L,
		0xd353a2b8e8f8e39eL, 0x9e114ebc1db98d8aL, 0x45f2990e0e793559L, 0xd33341d365733912L,
		0xd94ce74cd1778bc7L, 0x86c8d3107e80031cL, 0x17a9c1adb1192d16L, 0x062be3de61db4c9eL,
		0x0193e55bf27b821eL, 0x425616ccd850d573L, 0x8bdcfef42e89644fL, 0x889fe19c5cdd0cd0L,
		0xb6c58349505a069cL, 0x88527f2c3c758c85L, 0x6b6cf9286dd29f59L, 0x9ea76bd9f1934602L,
		0xaf0268d258b617e8L, 0xe277ed1c3707eaa6L, 0x1b03240ae336b6fbL, 0xed5daf4e0c95df54L,
		0x42fa8c6b25d8b2bdL, 0xb81bdec9d8f3f1f8L, 0xbc81a1b675ae4bacL, 0x81b2a3faa26ad0dcL,
		0x38e9ce65ae3cf007L, 0x284e170985bea4a1L, 0x1cb2f5154f98437aL, 0x1e173ae30bdf9bfbL,
		0x1f3f32cdfae01485L, 0x5fa4ea3a19501dc7L, 0xa318c69258ed2395L, 0x57be48d66ffca2dbL,
		0xc80e2df139529e15L, 0xe9ddca96a32502cdL, 0x69ca89427d93288cL, 0x5cfe9b16e26bca3dL,
		0xc8a61e4d4498130dL, 0x8d8ff0a28d53408dL, 0xe7135e44b365a301L, 0xf47376721ea6b0ccL,
		0x204e1ed989e6916eL, 0xe5193f2c03c32fafL, 0x03b0a7fdab48558cL, 0xd531a52ea225ce57L,
		0xb337230bda964bb1L, 0x24fb471a041d13bfL, 0x4bfc897f70461504L, 0x29a153d0b5d0b674L,
		0x9d621e740aaa14baL, 0x5e892ad283faf8f0L, 0x52d12dec54530e12L, 0x5a1e39e4de2a20cdL,
		0xfcb1be0335f40df7L, 0x2d89b4e72c12fe66L, 0xc65586ce0a6e3f40L, 0xddf92f6157316b5eL,
		0xe876f5e18d65c89eL, 0x6b35a8fad154c6ceL, 0xafe748ad3cc075c7L, 0xcfb0ba6d35436430L,
		0x7d0d2ef92daab9d0L, 0x64a3c4b06c971b9dL, 0xdb71310f021951daL, 0x75e0278d15e37aa5L,
		0xd0d18702b846bfa7L, 0xe7592ad26017cbf2L, 0x8129e0359778ba7cL, 0xe8c5d0516dada995L,
		0xa6047c3c89c6459aL, 0xb073c25471b688f9L, 0xd9f9efbe72427752L, 0x955cf557db6bb30eL,
		0x316df3fab0194a6aL, 0xe9c8a9be128db0d3L, 0x02027e5e5bfd10aeL, 0xe3444c3b1c80d467L,
		0xe3c624b1914bd0a4L, 0x78c2ab396033261aL, 0x5db9a3b9ddd5daf1L, 0xed64a9d10d64d395L,
		0x67cab037e7c594e6L, 0x9ae5a71bd2679b1aL, 0x07bf96017f7c9629L, 0x472cdb75b55fc7f6L,
		0x53c532e458fded7bL, 0x9d23162c36b0d761L, 0xd575555da7712b24L, 0x1829807893415606L,
		0xf419569f35bdac03L, 0xbdeaf19bb0910575L, 0x0d30fddb1fcc0be6L, 0xe264f441412e67a2L,
		0x3b5dde40003d11baL, 0x1c0a3f1b960eecbaL, 0xa92c1f9012301f49L, 0xa4b4e184591a0b99L,
		0xf3e25dc31e9bb1e1L, 0x1cf79ba85889bbdcL, 0xb549c85571c7e927L, 0x15a6234241dd3d66L,
		0x9bbe6161d5bf7fb9L, 0x3e94a904c1d5660bL, 0x1f817d27df5ede4dL, 0x398be018bd310fb5L,
		0x96126c1e1ff9bd91L, 0x9cbbfaf8560739e7L, 0x50b0711f5af445dbL, 0x80c0b90b26fc0ecdL,
		0xb3dd43871eaa7a2aL, 0x2748da2053d2cf68L, 0x5d9d0492c0aff08eL, 0x30ba84d55d6b4e80L,
		0x045544d16d6c7ee9L, 0xbe326759886c1df2L, 0x3ff48346cb9585a9L, 0xa242b5caef7faeb4L,
		0x5725c15b63c0e4dfL, 0x791f19ca44b6936dL, 0x66e1144bbfb2a0adL, 0x01d6c693df18d4b9L,
		0xbd85e6896b992241L, 0xe049a665b648076bL, 0xe4370d6c8be7da12L, 0xd05ac7121bb3591dL,
		0x5cc71d44df54a21fL, 0xc0ff6f30f24ba260L, 0xee952bc27e4d6f6aL, 0x39131e8ace5f78afL,
		0xbfc4facc0177363fL, 0x75e5f03d1ecfdaacL, 0xfd8d39ff4e7d20c3L, 0x8ba3c8802c185d88L,
		0x4c5a24a76d85bec3L, 0xd22af8352c710c8dL, 0xc2574da33566d41fL, 0x2c654a60d8ba6f5dL,
		0x42c0bce712e11c80L, 0x450f02908b825f15L, 0x602ebe5d30245e20L, 0x4bdf56061d1a4124L,
		0x8533e1aed8d4f188L, 0xad8007b6bb6831b0L, 0x8e53dfdb4b9559e6L, 0xbb888efc56f185d8L,
		0x9517eeffe0fcfd4dL, 0x1b775c5ad6fb8bf6L, 0xca1bf7937b358b4fL, 0xe90bdf28dbcd3152L,
		0x4cdbdb7a28991271L, 0x97ab25ed2f7e1932L, 0xaca0ca9671f594cdL, 0x9f1911165e3617f4L,
		0x1d4b4bb19eefa27bL, 0x194c8bad22688563L, 0x363ef709b44c3f16L, 0x71b3f89fc2e3df87L,
		0xa5fdf44c7f542b46L, 0xc5c46233c02972beL, 0xe9f12cebb017b9e7L, 0xf30f3509ca176635L,
		0x6ccfafebfcba2642L, 0x7ec427639de49212L, 0x90f32e25adb209fbL, 0x6ac8ab454927df1eL,
		0x0c88f69e01dd9f8eL, 0xba35894826c86501L, 0x856866449182fbfaL, 0x9516474aaa4ba2fcL,
		0x4749762c1f0c6fb7L, 0x71bb4a4cd6f286eeL, 0xaf5b9a68bfd0a35aL, 0xfec163448b4f5fbfL,
		0x31064cf3405648f0L, 0x3969f4f0ed25c926L, 0xe502b6f5f1b9e3d1L, 0xa38ebddd32f4e5c8L,
		0x2ea3f41e1b7433c2L, 0x27e7a8ea7d4d4371L, 0xaa0256d0cdf67ea0L, 0xad2941a63120f35eL,
		0x8e21979248bb5a78L, 0x3b94d518bf63250eL, 0x9b7a255f05a2b57dL, 0x51dcbe32463d36d0L,
		0x7134705b97df4d9aL, 0x07d27ec803701b34L, 0x79ec69bcd26b3cd0L, 0x6090672aa0a2da73L,
		0x4a8312ae2ff6125aL, 0x331b42ac0972ed7fL, 0x199963475c0e2a17L, 0xecf69b8970ad5539L,
		0x61f4ca937dde5886L, 0x1f30469f274e1b90L, 0x35e00938e61104eeL, 0xf5c8a9a4787e2d0bL,
		0x425a9a3603c77379L, 0xbe90b29c70ca9752L, 0x7fd491875b1b343fL, 0x3bb6f583a6e88348L,
		0x97969c6c3ac362baL, 0xbfff9f4aa0f79415L, 0x96545ec61c9bd155L, 0x414045d6393ce369L,
		0xfe83a3593901d798L, 0xbcd7192ba1d45fbcL, 0xf787e0b47617474cL, 0x1b1b7a29ac69eeb1L,
		0xf0b8c110260a3c7eL, 0x3e635cd1e1a2f8e4L, 0xbf72847084e604b5L, 0xd81dc002210e662cL,
		0x51a1f99677117e5dL, 0xe65b5deaab205771L, 0x4fbdc2c95b8995a1L, 0xbd88d5f517fac94cL,
		0xb1e6e68e076d0abfL, 0x8018a6f03792b808L, 0x3704c28db17f4695L, 0x1aa18333d1589b4bL,
		0x5cbbbbf1d547a311L, 0x6acc6fe57795cd7cL, 0x5a27fd14539fc1c9L, 0x6d0e9dfe17d7b2f1L,
		0x2ad1640def3b0f46L, 0xe2e282c92d418e6bL, 0xfb992d766616dd23L, 0xcda85d80da427e8eL,
		0xef8d8502abee2278L, 0xd28fe89204ef3369L, 0x6834028c447f8fc3L, 0x1dbbca5ad9723834L,
		0x5441fa5c1f8c44e9L, 0xd3a835d6f73befddL, 0xde38c8e324ac7e53L, 0x6cf1e70a13851309L,
		0xd5a4016877dea292L, 0xfbf7be7394fe09f8L, 0xffe162e780ed1c33L, 0x78ce030222787822L,
		0xeb768d2af3f4caadL, 0xd1931723603511dbL, 0x3828724707339818L, 0x308b9dc9c97cc652L,
		0xf76fd39c2d653f6eL, 0xd5255b704c0c0b75L, 0xf415899f54946643L, 0xc2c0c674e0fc73c7L,
		0x1d817d1bc1f7cdbaL, 0x2c9bc1d38df9a358L, 0x6b0b908839a02efaL, 0x9c8aae8fc42eaab3L,
		0xd6d4de5c847c81a3L, 0x8d7da44657018463L, 0x34b0aaaa512d899cL, 0x87d5962bfa2444e3L,
		0x85d3d5a3a5cd1be0L, 0xcdab8e6f45e07b67L, 0x22e3f05564002663L, 0x104d11d8217bcb14L,
		0x2f656b63a5dc894eL, 0x78d7b8f69861e105L, 0xaf07633a57ee27f5L, 0x750661b6725a31f3L,
		0xe347ca82b590d876L, 0x6310610e9def4a4aL, 0xcd3b0ae78a833564L, 0xa38133021633b8ccL,
		0x7c6ddfaa3844f716L, 0x68c4566c1b72b7b8L, 0x2417661935b66dbbL, 0xb2345f5df9eee447L,
		0x373aef651c65fa16L, 0xafc28337c938628fL, 0x685a42a06e458a55L, 0x0f2c2e1e166459c3L,
		0x42c86d5b256a5fa8L, 0x81d4445710a28c1fL, 0xbb3277e5935982a1L, 0x3cf10cd8471fee6bL,
		0x6c8229d8f746f7edL, 0xbede1479e7791167L, 0x352a27383e5f4cffL, 0x11c70f485ce7dbe3L,
		0x8563a34a848ecb7dL, 0xb1305a5f8dd66ae5L, 0x4aaa72b51f6d5abcL, 0xa24f9d31bd359d29L,
		0x9a1c586e2af996eeL, 0xe0e6851a148d1d1eL, 0x91eac662ed0704c4L, 0x71d70f99bdb1d9abL,
		0x3dfde387844d0140L, 0x84f3ac027e2e1ad1L, 0x771636d573f537a2L, 0xbb0f6970ea6a0022L,
		0xd6063031524f3683L, 0x6b450e760c14b4f9L, 0x4766874137588552L, 0x6f6db2b52fe74807L,
		0x4e039b6241dbd5eaL, 0x7fca257462966b6fL, 0x717f20505b4583b4L, 0xf9afab246367b2ffL,
		0xebf46540e121a046L, 0x73cec0c86109c2c2L, 0x7f8ab35174170359L, 0x67817dd6fd4bcbe6L,
		0xe60a4a2c7ce608b5L, 0x86374d46c7952a22L, 0xbdaa8cfcd9abaedbL, 0xcfc3798147678e3fL,
		0x1cf5f4a72679ef1dL, 0xa2c5b7028bec6d6cL, 0xcad202a33eaf8efaL, 0x6f463aee03ae3b6cL,
		0x1f2a450668391a72L, 0xa121d3025427423cL, 0x8e147641fa49165aL, 0x97a9a97b1942b94cL,
		0x490e0a5784df8a7cL, 0x8bca18578c1c7febL, 0xd72c70cc4d49de47L, 0xc8b3995d0e3a1149L,
		0x3bb76e8387b795d6L, 0xf8fcbf1543eff0d4L, 0x14c32d764bde48edL, 0xbc0e24e9e706ac81L,
		0x8aee9af45168dcdeL, 0x3166027ae640dcc3L, 0x5565fa46bc01b33dL, 0xb6112879bc42e75eL,
		0x1ad167470d516d90L, 0x3ab233270a0de407L, 0x48a6c74f63160deaL, 0x90b66d3bf50c525fL,
		0x8a3b24ed31d8d8deL, 0x03e8024a7395fdffL, 0xbed17c33f5b21530L, 0xb732ff02ce49db8dL,
		0xad2b5020cb3c4720L, 0x8eb04ba3beb2df96L, 0xc711b531c2e02baaL, 0xa3cd2cb272a33d80L,
		0x95dd892e6d926719L, 0xeb594261c7dfafbaL, 0x796fd35f6ca3c64eL, 0xbfce50b9eaa4031cL,
		0xfaf66b4d75274146L, 0xff29ac77b0e5cc05L, 0xfadb3c50b80f16e5L, 0x43bb58b4d375549bL,
		0x4c0c471370c2a2f2L, 0xf43431bfd3aac80fL, 0xe53c866b7383ec99L, 0xffd710b7c403c9f9L,
		0xdd148077d4c1fd5cL, 0xa6ee30b9f138cabaL, 0x708d288f29c1370dL, 0x397bac4791f932aeL,
		0x28979a7107b5e3e4L, 0x39266d5cc3c97e7dL, 0x5f84cc5daf00047aL, 0x74f6ca0a12da5243L,
		0x82ab3585a748a77fL, 0x7149bf0d8470a69fL, 0xdb3d6c6d6a7fd121L, 0xa367205aef0fadadL,
		0x7775ac70072c9738L, 0xc638adc5f261590dL, 0x05396cf28c5fba32L, 0xed03ffb2e5b97059L,
		0x9067f5bdbe351db0L, 0x10a9b9fd79fa6b58L, 0x54d5bd3395827b1aL, 0x13712905022adeaaL,
		0x4c2846f2eaa8d9cfL, 0x4b07f177bc18a10cL, 0x2bd40d3413edf8e6L, 0x0d386ab445518fa8L,
		0x3d91a21c5e088d52L, 0x19b18759cdf8fcfdL, 0x01fdd988966ef3baL, 0x368b48b5d58bfa69L,
		0x3359b2917223a314L, 0x30f97440639a60e9L, 0xd12daf9177627c9fL, 0xb52a768a57f8300bL,
		0x151457c399d36a4aL, 0x2ee89da379f99c12L, 0x9fb88bac59480f16L, 0xb421d7c71cb582a1L,
		0x3d42cfdb051c63b7L, 0x5094e223efc89214L, 0xd032dad07bc5396cL, 0xf05dbb14ff777495L,
		0xc880a0ea9a8b932eL, 0x96c30e722f9e41efL, 0xef697e4a413d6ea2L, 0x81241aabc07127d9L,
		0xa64784211442885fL, 0xad6dfebba2790a16L, 0x5b386d261980dff7L, 0x1aa36f461e34f38bL,
		0xbb12a85cf4cda3fbL, 0xe59b134cff1d41adL, 0x5f3abf7ec4566ea6L, 0x56e4817d04ea843bL,
		0xe01cf339094e2802L, 0x5efa7dab8513e642L, 0x7e0d3dbb55b8eee6L, 0xe3b005c44cfc62d8L,
		0x090702fcb2811433L, 0x7e46e87f779dec5bL, 0x56fc33e157eaf78bL, 0x609c898f87684597L,
		0x7ffc539986558611L, 0xb637d30c712789b6L, 0xbdb74dd3ec42bf7fL, 0x1d38b12425ddcf86L,
		0xa38eba1dca066f7eL, 0x92aec701678c98b6L, 0x6eafe8cc1b5e3579L, 0x6c255325b7745ed4L,
		0xa157e72eb6178d81L, 0x49546d4ae3bc3bbcL, 0x8c7bfa96b3294080L, 0x446872fee94f54feL,
		0x04a7a4181d7cddf2L, 0x026a2215f97049b1L, 0xa80a2374140d2d32L, 0xe8be3c08c22adbf6L,
		0x531c265c984217d6L, 0x0a9b4f74fe6764c5L, 0x48ef941f66711501L, 0x95b1d810f1d028a4L,
		0x27215abf9bfb6bdfL, 0xd29d1598212ecd2eL, 0x267582bed8aa46aaL, 0xc8ff3025b7ec03bbL,
		0x53105847e28330e3L, 0x45d8d0f0a78362afL, 0x988f77b6da11f6e3L, 0xa660757cee135096L,
		0x7da856279576afefL, 0xdc30fcd5447440dbL, 0xc5e1cdd2b041eed7L, 0x5fdba8abad3820d9L,
		0xda517a125e33c1b2L, 0xb7d117f523823ddeL, 0xb0d8d316eef79d66L, 0x1daf648cb5acb28fL,
		0xf88c3f1bacd5c685L, 0x91b3afd84a523d7fL, 0x36cf06409a0fed1dL, 0x6c2823db22a77bdeL,
		0xc1e253762c284388L, 0x62dd1cc33243ff53L, 0xad7156aa8cd0fb74L, 0xdc6934b38938f532L,
		0xbdd04da987de285bL, 0x3ef325cefa015f8cL, 0x370c2dbb13274416L, 0xdc262b9c7b95689fL,
		0xf245533ae4c4870dL, 0x6fe2d8a19d4c1ee7L, 0xd59ccb9de51a6badL, 0x994ce1d0bbbdf5d0L,
		0xd33bd5af7bbce745L, 0xd1e4973b06f48ebdL, 0x266019f0ebd70251L, 0x9cfd3f42178ddf7aL,
		0xe7027a9e2de26a6aL, 0x8fd52f08a9f07381L, 0xfa094d52068812fcL, 0x6c0150ca91c29370L,
		0x51e30806202f09f4L, 0xee2bbef3a888a32bL, 0xc292aaee7b64c74cL, 0xe9711f7438020d11L,
		0x3b81082002e7a648L, 0xb6154449e79797a8L, 0x4662ff5aace91bcbL, 0x8adc28490410d34dL,
		0x9fb51c41008297e1L, 0x7758ce67f95c6323L, 0x1aa7bc9d23226d9eL, 0xae90fb1c24b39ebeL,
		0xb46915d722a2a7b9L, 0x08dc6dccd032b11cL, 0x0a6ce5abad4c4bd9L, 0x8ebc7758d7a37339L,
		0xbe011d4567d09fe6L, 0x782845f6d2ce4d33L, 0x9ae6ff3503371ea1L, 0x04f6dc6dbba1dea4L
	};
}
