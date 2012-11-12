package r.data;

import r.*;
import r.data.internal.*;

// FIXME: add conversion to scalar representation to copies (also other types that have scalar representations)

public interface RInt extends RNumber {
    int NA = Integer.MIN_VALUE;
    String TYPE_STRING = "integer";

    ScalarIntImpl BOXED_NA = RIntFactory.getScalar(NA);
    ScalarIntImpl BOXED_ZERO = RIntFactory.getScalar(0);
    ScalarIntImpl BOXED_ONE = RIntFactory.getScalar(1);

    IntImpl EMPTY = (IntImpl) RIntFactory.getUninitializedArray(0);

    int getInt(int i);
    RInt set(int i, int val);
    RInt materialize();

    public class RIntFactory {
        public static ScalarIntImpl getScalar(int value) {
            return new ScalarIntImpl(value);
        }
        public static RInt getArray(int... values) {
            if (values.length == 1) {
                return new ScalarIntImpl(values[0]);
            }
            return new IntImpl(values);
        }
        public static RInt getUninitializedArray(int size) {
            if (size == 1) {
                return new ScalarIntImpl(0);
            }
            return new IntImpl(size);
        }
        public static RInt getNAArray(int size) {
            if (size == 1) {
                return new ScalarIntImpl(NA);
            }
            IntImpl v = (IntImpl) getUninitializedArray(size);
            for (int i = 0; i < size; i++) {
                v.set(i, NA);
            }
            return v;
        }
        public static RInt copy(RInt i) {
            if (i.size() == 1) {
                return new ScalarIntImpl(i.getInt(0));
            }
            return new IntImpl(i);
        }
        public static RInt getForArray(int[] values) {  // re-uses values!
            if (values.length == 1) {
                return new ScalarIntImpl(values[0]);
            }
            return new IntImpl(values, false);
        }
        public static RInt forSequence(int from, int to, int step) {
            return new IntImpl.RIntSequence(from, to, step);
        }
        public static RInt exclude(int excludeIndex, RInt orig) {
            return new RIntExclusion(excludeIndex, orig);
        }
        public static RInt subset(RInt value, RInt index) {
            return new RIntSubset(value, index);
        }
    }

    public static class RDoubleView extends View.RDoubleView implements RDouble {

        final RInt rint;
        public RDoubleView(RInt rint) {
            this.rint = rint;
        }

        public int size() {
            return rint.size();
        }

        @Override
        public RInt asInt() {
            return rint;
        }

        @Override
        public RAttributes getAttributes() {
            return rint.getAttributes();
        }

        @Override
        public RLogical asLogical() {
            return rint.asLogical();
        }

        @Override
        public double getDouble(int i) {
            int v = rint.getInt(i);
            return Convert.int2double(v);
        }

        @Override
        public boolean isSharedReal() {
            return rint.isShared();
        }

        @Override
        public void ref() {
            rint.ref();
        }
    }

    public static class RLogicalView extends View.RLogicalView implements RLogical {

        final RInt rint;
        public RLogicalView(RInt rint) {
            this.rint = rint;
        }

        public int size() {
            return rint.size();
        }

        @Override
        public RInt asInt() {
            return rint;
        }

        @Override
        public RAttributes getAttributes() {
            return rint.getAttributes();
        }

        @Override
        public RDouble asDouble() {
            return rint.asDouble();
        }

        @Override
        public int getLogical(int i) {
            int v = rint.getInt(i);
            return Convert.int2logical(v);
        }

        @Override
        public boolean isSharedReal() {
            return rint.isShared();
        }

        @Override
        public void ref() {
            rint.ref();
        }
    }

    public static class RIntExclusion extends View.RIntView implements RInt {

        final RInt orig;
        final int excludeIndex;
        final int size;

        public RIntExclusion(int excludeIndex, RInt orig) {
            this.orig = orig;
            this.excludeIndex = excludeIndex;
            this.size = orig.size() - 1;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int getInt(int i) {
            Utils.check(i < size, "bounds check");
            Utils.check(i >= 0, "bounds check");

            if (i < excludeIndex) {
                return orig.getInt(i);
            } else {
                return orig.getInt(i + 1);
            }
        }

        @Override
        public boolean isSharedReal() {
            return orig.isShared();
        }

        @Override
        public void ref() {
            orig.ref();
        }
    }

    // indexes must all be positive
    //   but can be out of bounds ==> NA's are returned in that case
    public static class RIntSubset extends View.RIntView implements RInt {

        final RInt value;
        final int vsize;
        final RInt index;
        final int isize;

        public RIntSubset(RInt value, RInt index) {
            this.value = value;
            this.index = index;
            this.isize = index.size();
            this.vsize = value.size();
        }

        @Override
        public int size() {
            return isize;
        }

        @Override
        public int getInt(int i) {
            int j = index.getInt(i);
            if (j > vsize) {
                return RInt.NA;
            } else {
                return value.getInt(j - 1);
            }
        }

        @Override
        public boolean isSharedReal() {
            return value.isShared() || index.isShared();
        }

        @Override
        public void ref() {
            value.ref();
            index.ref();
        }
    }
}
