public class OnXJava7StyleOn8 {
  @interface Foo {
    String value() default "";
  }
  @interface Bar {
    String stuff() default "";
  }
  @lombok.Getter() String a;
  @lombok.Setter() String b;
  @lombok.Setter() String c;
  @lombok.Setter() String d;
  @lombok.Getter() String e;
  public OnXJava7StyleOn8() {
    super();
  }
  public @Foo @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") String getA() {
    return this.a;
  }
  public @Foo() @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void setB(final String b) {
    this.b = b;
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void setC(final @Foo("a") String c) {
    this.c = c;
  }
  public @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") void setD(final @Bar(stuff = "b") String d) {
    this.d = d;
  }
  public @Foo(value = "c") @Bar(stuff = "d") @java.lang.SuppressWarnings("all") @javax.annotation.Generated("lombok") String getE() {
    return this.e;
  }
}
