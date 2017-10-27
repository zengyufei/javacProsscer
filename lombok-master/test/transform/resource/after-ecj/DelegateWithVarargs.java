import lombok.experimental.Delegate;
class DelegateWithVarargs {
  private interface Bar {
    void justOneParameter(int... varargs);
    void multipleParameters(String first, int... varargs);
    void array(int[] array);
    void arrayVarargs(int[]... arrayVarargs);
  }
  private @Delegate Bar bar;
  DelegateWithVarargs() {
    super();
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void array(final int[] array) {
    this.bar.array(array);
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void arrayVarargs(final int[]... arrayVarargs) {
    this.bar.arrayVarargs(arrayVarargs);
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void justOneParameter(final int... varargs) {
    this.bar.justOneParameter(varargs);
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void multipleParameters(final java.lang.String first, final int... varargs) {
    this.bar.multipleParameters(first, varargs);
  }
}
