/* Skeleton code copyright (C) 2008, 2022 Paul N. Hilfinger and the
 * Regents of the University of California.  Do not distribute this or any
 * derivative work without permission. */

package ataxx;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Formatter;

import java.util.function.Consumer;

import static ataxx.PieceColor.*;
import static ataxx.GameException.error;

/** An Ataxx board.   The squares are labeled by column (a char value between
 *  'a' - 2 and 'g' + 2) and row (a char value between '1' - 2 and '7'
 *  + 2) or by linearized index, an integer described below.  Values of
 *  the column outside 'a' and 'g' and of the row outside '1' to '7' denote
 *  two layers of border squares, which are always blocked.
 *  This artificial border (which is never actually printed) is a common
 *  trick that allows one to avoid testing for edge conditions.
 *  For example, to look at all the possible moves from a square, sq,
 *  on the normal board (i.e., not in the border region), one can simply
 *  look at all squares within two rows and columns of sq without worrying
 *  about going off the board. Since squares in the border region are
 *  blocked, the normal logic that prevents moving to a blocked square
 *  will apply.
 *
 *  For some purposes, it is useful to refer to squares using a single
 *  integer, which we call its "linearized index".  This is simply the
 *  number of the square in row-major order (counting from 0).
 *
 *  Moves on this board are denoted by Moves.
 *  @author Neman Faiz
 */
class Board {

    /** Number of squares on a side of the board. */
    static final int SIDE = Move.SIDE;

    /** Length of a side + an artificial 2-deep border region.
     * This is unrelated to a move that is an "extend". */
    static final int EXTENDED_SIDE = Move.EXTENDED_SIDE;

    /** Overall size of the board. */
    private int boardSize = EXTENDED_SIDE * EXTENDED_SIDE;

    /** Number of consecutive non-extending moves before game ends. */
    static final int JUMP_LIMIT = 25;

    /** A new, cleared board in the initial configuration. */
    Board() {
        _board = new PieceColor[EXTENDED_SIDE * EXTENDED_SIDE];

        setNotifier(NOP);
        clear();
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        _board = board0._board.clone();

        _whoseMove = board0._whoseMove;
        _numJumps = board0._numJumps;

        _totalOpen = board0._totalOpen;
        _numPieces = board0._numPieces.clone();

        _allMoves = new ArrayList<>(board0.allMoves());

        _undoSquares = new Stack<>();
        _undoPieces = new Stack<>();

        setNotifier(NOP);
    }

    /** Return the linearized index of square COL ROW. */
    static int index(char col, char row) {
        return (row - '1' + 2) * EXTENDED_SIDE + (col - 'a' + 2);
    }

    /** Return the linearized index of the square that is DC columns and DR
     *  rows away from the square with index SQ. */
    static int neighbor(int sq, int dc, int dr) {
        return sq + dc + dr * EXTENDED_SIDE;
    }

    /** Clear me to my starting state, with pieces in their initial
     *  positions and no blocks. */
    void clear() {

        _whoseMove = RED;
        _numJumps = 0;

        for (int i : _numPieces) {
            i = 0;
        }

        _allMoves = new ArrayList<>();
        _undoSquares = new Stack<>();
        _undoPieces = new Stack<>();

        Arrays.fill(_board, BLOCKED);

        for (char col = 'a'; col < 'g' + 1; col++) {
            for (char row = '1'; row < '7' + 1; row++) {
                unrecordedSet(col, row, EMPTY);
            }
        }

        unrecordedSet('a', '1', BLUE);
        unrecordedSet('g', '1', RED);
        unrecordedSet('a', '7', RED);
        unrecordedSet('g', '7', BLUE);

        _numPieces[2] = 2;
        _numPieces[3] = 2;

        _totalOpen = JUMP_LIMIT * 3;

        _winner = null;


        announce();
    }



    /** Return the winner, if there is one yet, and otherwise null.  Returns
     *  EMPTY in the case of a draw, which can happen as a result of there
     *  having been MAX_JUMPS consecutive jumps without intervening extends,
     *  or if neither player can move and both have the same number of pieces.*/
    PieceColor getWinner() {
        return _winner;
    }

    int getBoardSize() {
        return boardSize;
    }

    /** Return number of red pieces on the board. */
    int redPieces() {
        return numPieces(RED);
    }

    /** Return number of blue pieces on the board. */
    int bluePieces() {
        return numPieces(BLUE);
    }

    /** Return number of COLOR pieces on the board. */
    int numPieces(PieceColor color) {
        return _numPieces[color.ordinal()];
    }

    /** Increment numPieces(COLOR) by K. */
    private void incrPieces(PieceColor color, int k) {
        _numPieces[color.ordinal()] += k;
    }

    /** The current contents of square CR, where 'a'-2 <= C <= 'g'+2, and
     *  '1'-2 <= R <= '7'+2.  Squares outside the range a1-g7 are all
     *  BLOCKED.  Returns the same value as get(index(C, R)). */
    PieceColor get(char c, char r) {
        return _board[index(c, r)];
    }

    /** Return the current contents of square with linearized index SQ. */
    PieceColor get(int sq) {
        return _board[sq];
    }

    /** Set get(C, R) to V, where 'a' <= C <= 'g', and
     *  '1' <= R <= '7'. This operation is undoable. */
    private void set(char c, char r, PieceColor v) {
        set(index(c, r), v);
    }

    /** Set square with linearized index SQ to V.  This operation is
     *  undoable. */
    private void set(int sq, PieceColor v) {
        addUndo(sq);
        _board[sq] = v;
    }

    /** Set square at C R to V (not undoable). This is used for changing
     * contents of the board without updating the undo stacks. */
    private void unrecordedSet(char c, char r, PieceColor v) {
        _board[index(c, r)] = v;
    }

    /** Set square at linearized index SQ to V (not undoable). This is used
     * for changing contents of the board without updating the undo stacks. */
    private void unrecordedSet(int sq, PieceColor v) {
        _board[sq] = v;
    }

    /** Return true iff MOVE is legal on the current board. */
    boolean legalMove(Move move) {

        if (move == null) {
            return false;
        }
        if (move.isPass()) {
            return !canMove(_whoseMove);
        }

        if (move.col0() < 'a' || move.col0() > 'g'
                || move.col1() < 'a' || move.col1() > 'g'
                || move.row0() < '1' || move.row0() > '7'
                || move.row1() < '1' || move.row1() > '7') {
            return false;
        }

        if (get(move.col0(), move.row0())
                != _whoseMove || get(move.col1(),
                move.row1()) != EMPTY) {
            return false;
        }

        if (move.col1() - move.col0() < -2
                || move.col1() - move.col1() > 2
                || move.row1() - move.row0() < -2
                || move.row1() - move.row0() > 2) {
            return false;
        }

        return true;

    }

    /** Return true iff C0 R0 - C1 R1 is legal on the current board. */
    boolean legalMove(char c0, char r0, char c1, char r1) {
        return legalMove(Move.move(c0, r0, c1, r1));
    }

    /** Return true iff player WHO can move, ignoring whether it is
     *  that player's move and whether the game is over. */
    boolean canMove(PieceColor who) {
        for (int board = 0; board < _board.length; board++) {

            if (who == get(board)) {

                for (int col = -2; col < 3; col++) {
                    for (int row = -2; row < 3; row++) {

                        if (get(neighbor(board, col, row)) == EMPTY) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }


    /** Return the color of the player who has the next move.  The
     *  value is arbitrary if the game is over. */
    PieceColor whoseMove() {
        return _whoseMove;
    }

    /** Return total number of moves and passes since the last
     *  clear or the creation of the board. */
    int numMoves() {
        return _allMoves.size();
    }

    /** Return number of non-pass moves made in the current game since the
     *  last extend move added a piece to the board (or since the
     *  start of the game). Used to detect end-of-game. */
    int numJumps() {
        return _numJumps;
    }

    /** Assuming MOVE has the format "-" or "C0R0-C1R1", make the denoted
     *  move ("-" means "pass"). */
    void makeMove(String move) {
        if (move.equals("-")) {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(move.charAt(0), move.charAt(1), move.charAt(3),
                               move.charAt(4)));
        }
    }

    /** Perform the move C0R0-C1R1, or pass if C0 is '-'.  For moves
     *  other than pass, assumes that legalMove(C0, R0, C1, R1). */
    void makeMove(char c0, char r0, char c1, char r1) {
        if (c0 == '-') {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(c0, r0, c1, r1));
        }
    }

    /** Make the MOVE on this Board, assuming it is legal. */
    void makeMove(Move move) {
        if (!legalMove(move)) {
            throw error("Illegal move: %s", move);
        }
        if (move.isPass()) {
            pass();
            return;
        }
        _allMoves.add(move);
        startUndo();
        PieceColor opponent = _whoseMove.opposite();
        if (move.isExtend()) {
            set(move.col1(), move.row1(), _whoseMove);
            incrPieces(_whoseMove, 1);
            _numJumps = 0; _numMoves++; _totalOpen -= 1; _undoPieces.add(EMPTY);
            for (int col = -1; col < 2; col++) {
                for (int row = -1; row < 2; row++) {
                    if (get(
                            (char) (move.col1() + col),
                            (char) (move.row1() + row))
                            == _whoseMove.opposite()) {
                        set(
                                (char) (move.col1() + col),
                                (char) (move.row1() + row), _whoseMove);
                        _undoPieces.add(_whoseMove.opposite());
                        incrPieces(_whoseMove, 1);
                        incrPieces(_whoseMove.opposite(), -1);
                    }
                }
            }
        }
        if (move.isJump()) {
            set(move.col1(), move.row1(), whoseMove());
            _undoPieces.add(EMPTY);
            set(move.col0(), move.row0(), EMPTY);
            _undoPieces.add(_whoseMove);
            _numJumps += 1; _numMoves++; _totalOpen -= 1;
            for (int col = -1; col < 2; col++) {
                for (int row = -1; row < 2; row++) {
                    if (get(
                            (char) (move.col1() + col),
                            (char) (move.row1() + row))
                            == _whoseMove.opposite()) {
                        set(
                                (char) (move.col1() + col),
                                (char) (move.row1() + row), _whoseMove);
                        _undoPieces.add(_whoseMove.opposite());
                        incrPieces(_whoseMove, 1);
                        incrPieces(_whoseMove.opposite(), -1);
                    }
                }
            }
        }

        declareWinner(opponent);

        _whoseMove = opponent;

        announce();
    }

    private void declareWinner(PieceColor opponent) {
        if (redPieces() + bluePieces() == totalOpen()) {
            if (redPieces() == bluePieces()) {
                _winner = EMPTY;
            } else if (redPieces() > bluePieces()) {
                _winner = RED;
            } else if (bluePieces() > redPieces()) {
                _winner = BLUE;
            }
        } else if (redPieces() == 0) {
            _winner = BLUE;
        } else if (bluePieces() == 0) {
            _winner = RED;
        } else if (numJumps() >= JUMP_LIMIT) {
            if (redPieces() == bluePieces()) {
                _winner = EMPTY;
            } else if (redPieces() > bluePieces()) {
                _winner = RED;
            } else if (bluePieces() > redPieces()) {
                _winner = BLUE;
            }
        } else if (!canMove(_whoseMove) && !canMove(opponent)) {
            if (redPieces() > bluePieces()) {
                _winner = RED;
            } else if (bluePieces() > redPieces()) {
                _winner = BLUE;
            } else {
                _winner = EMPTY;
            }
        }
    }

    /** Update to indicate that the current player passes, assuming it
     *  is legal to do so. Passing is undoable. */
    void pass() {
        assert !canMove(_whoseMove);

        startUndo();
        _whoseMove = _whoseMove.opposite();

        _allMoves.add(Move.pass());

        announce();
    }

    /** Undo the last move. */
    void undo() {

        if (_undoSquares.peek() == null) {
            _undoSquares.pop();
            _undoPieces.pop();

            _undoSquares.pop();
            _undoPieces.pop();
        }

        while (_undoSquares.peek() != null) {
            Integer moveVar = _undoSquares.pop();
            PieceColor colorVar = _undoPieces.pop();

            if (get(moveVar).isPiece()) {
                incrPieces(get(moveVar), -1);
            }
            incrPieces(colorVar, 1);
            unrecordedSet(moveVar, colorVar);

        }

        _undoSquares.pop();
        _undoPieces.pop();

        if (_allMoves.get(_allMoves.size() - 1).isJump()) {
            _numJumps--;
        }

        _numMoves--;

        _whoseMove = _whoseMove.opposite();
        _allMoves.remove(_allMoves.size() - 1);
        _winner = null;
        announce();
    }

    /** Indicate beginning of a move in the undo stack. See the
     * _undoSquares and _undoPieces instance variable comments for
     * details on how the beginning of moves are marked. */
    private void startUndo() {
        _undoSquares.add(null);
        _undoPieces.add(null);
    }

    /** Add an undo action for changing SQ on current board. */
    private void addUndo(int sq) {
        _undoSquares.push(sq);
    }

    /** Return true iff it is legal to place a block at C R. */
    boolean legalBlock(char c, char r) {
        if (numMoves() != 0) {
            return false;
        }

        PieceColor vertical = get(verticalReflection(c, r));
        PieceColor diagonal = get(diagonalReflection(c, r));
        PieceColor horizontal = get(horizontalReflection(c, r));

        if (vertical == EMPTY && diagonal == EMPTY && horizontal == EMPTY) {
            return true;
        }
        return false;
    }

    /** Return true iff it is legal to place a block at CR. */
    boolean legalBlock(String cr) {
        return legalBlock(cr.charAt(0), cr.charAt(1));
    }

    private int verticalReflection(char col, char row) {


        if (row - '4' < 0) {

            int distToTop = (Math.abs(row - '4'));
            if (distToTop == 0) {
                return index(col, row);
            }

            char reflectUpWard = (char) (row + 2 * distToTop);

            return index(col, (reflectUpWard));




        } else {

            int distToBottom = (Math.abs(row - '4'));
            if (distToBottom == 0) {
                return index(col, row);
            }

            char reflectDownWard = (char) (row - 2 * distToBottom);


            return index(col, (reflectDownWard));

        }
    }

    private int diagonalReflection(char col, char row) {

        if (index(col, row) - index('d', '4') < 0) {

            int distToDiagonal = (Math.abs(index(col, row) - index('d', '4')));

            if (distToDiagonal == 0) {
                return index(col, row);
            }

            return (index(col, row) + 2 * distToDiagonal);




        } else {

            int distToDiagonal = (Math.abs(index(col, row) - index('d', '4')));

            if (distToDiagonal == 0) {
                return index(col, row);
            }

            return  (index(col, row) - 2 * distToDiagonal);

        }

    }
    private int horizontalReflection(char col, char row) {

        if (col - 'd' < 0) {

            int distToRight = (Math.abs(col - 'd'));
            if (distToRight == 0) {
                return index(col, row);
            }

            char reflectToRight = (char) (col + 2 * distToRight);

            return index((reflectToRight), row);



        } else {

            int distToLeft = (Math.abs(col - 'd'));
            if (distToLeft == 0) {
                return index(col, row);
            }

            char reflectToLeft = (char) (col - 2 * distToLeft);
            return index((reflectToLeft), row);


        }
    }



    /** Set a block on the square C R and its reflections across the middle
     *  row and/or column, if that square is unoccupied and not
     *  in one of the corners. Has no effect if any of the squares is
     *  already occupied by a block.  It is an error to place a block on a
     *  piece. */
    void setBlock(char c, char r) {
        if (!legalBlock(c, r)) {
            throw error("illegal block placement");
        }
        if (index(c, r) == index('d', '4')) {
            _board[index(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);
            _totalOpen -= 1;
        } else if (c == 'd') {
            _board[index(c, r)] = BLOCKED;

            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _board[verticalReflection(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _totalOpen -= 2;
        } else if (r == '4') {
            _board[index(c, r)] = BLOCKED;

            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _board[horizontalReflection(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _totalOpen -= 2;
        } else {
            _board[index(c, r)] = BLOCKED;

            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _board[verticalReflection(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _board[diagonalReflection(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);
            _board[horizontalReflection(c, r)] = BLOCKED;
            incrPieces(BLOCKED, 1);
            incrPieces(EMPTY, -1);

            _totalOpen -= 4;
        }



        if (!canMove(RED) && !canMove(BLUE)) {
            _winner = EMPTY;
        }


        announce();
    }

    /** Place a block at CR. */
    void setBlock(String cr) {
        setBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Return total number of unblocked squares. */
    int totalOpen() {
        return numPieces(EMPTY);
    }

    /** Return a list of all moves made since the last clear (or start of
     *  game). */
    List<Move> allMoves() {
        return _allMoves;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        }
        Board other = (Board) obj;
        return Arrays.equals(_board, other._board);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_board);
    }

    /** Return a text depiction of the board.  If LEGEND, supply row and
     *  column numbers around the edges. */
    String toString(boolean legend) {
        Formatter out = new Formatter();
        for (char r = '7'; r >= '1'; r -= 1) {
            if (legend) {
                out.format("%c", r);
            }
            out.format(" ");
            for (char c = 'a'; c <= 'g'; c += 1) {
                switch (get(c, r)) {
                case RED:
                    out.format(" r");
                    break;
                case BLUE:
                    out.format(" b");
                    break;
                case BLOCKED:
                    out.format(" X");
                    break;
                case EMPTY:
                    out.format(" -");
                    break;
                default:
                    break;
                }
            }
            out.format("%n");
        }
        if (legend) {
            out.format("   a b c d e f g");
        }
        return out.toString();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** Use _notifier.accept(this) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** For reasons of efficiency in copying the board,
     *  we use a 1D array to represent it, using the usual access
     *  algorithm: row r, column c => index(r, c).
     *
     *  Next, instead of using a 7x7 board, we use an 11x11 board in
     *  which the outer two rows and columns are blocks, and
     *  row 2, column 2 actually represents row 0, column 0
     *  of the real board.  As a result of this trick, there is no
     *  need to special-case being near the edge: we don't move
     *  off the edge because it looks blocked.
     *
     *  Using characters as indices, it follows that if 'a' <= c <= 'g'
     *  and '1' <= r <= '7', then row r, column c of the board corresponds
     *  to _board[(c -'a' + 2) + 11 (r - '1' + 2) ]. */
    private final PieceColor[] _board;

    /** Player that is next to move. */
    private PieceColor _whoseMove;

    /** Number of consecutive non-extending moves since the
     *  last clear or the beginning of the game. */
    private int _numJumps;

    /** Total number of unblocked squares. */
    private int _totalOpen;

    /** Number of blue and red pieces, indexed by the ordinal positions of
     *  enumerals BLUE and RED. */
    private int[] _numPieces = new int[BLUE.ordinal() + 1];

    /** Set to winner when game ends (EMPTY if tie).  Otherwise is null. */
    private PieceColor _winner;

    /** List of all (non-undone) moves since the last clear or beginning of
     *  the game. */
    private ArrayList<Move> _allMoves;

    /* The undo stack. We keep a stack of squares that have changed and
     * their previous contents.  Any given move may involve several such
     * changes, so we mark the start of the changes for each move (including
     * passes) with a null. */

    /** Stack of linearized indices of squares that have been modified and
     *  not undone. Nulls mark the beginnings of full moves. */
    private Stack<Integer> _undoSquares;
    /** Stack of pieces formally at corresponding squares in _UNDOSQUARES. */
    private Stack<PieceColor> _undoPieces;

    /** The total number of moves and passes since the last
     *  clear or the creation of the board. */
    private int _numMoves;

    /** numbers of Blocks placed on board. */
    private int _numBlocks;

}
