package com.zyf.result;

import java.io.Serializable;

public class Msg<T>  implements Serializable {

	public static final int SUCCESS_CODE = 200;
	public static final int ERROR_CODE = 501;

	private T result;
	private String msg;
	private Object code;

	public T getResult() {
		return result;
	}

	public String getMsg() {
		return msg;
	}

	public Object getCode() {
		return code;
	}

	public Msg(int status) {
		this.code = status;
	}

	public Msg(int status, String msg, T result) {
		this.code = status;
		this.msg = msg;
		this.result = result;
	}

	public static Msg.BodyBuilder status(int status) {
		return new Msg.DefaultBuilder(status);
	}

	public static Msg.BodyBuilder ok() {
		return status(SUCCESS_CODE);
	}

	public static <T> Msg<T> ok(T result) {
		Msg.BodyBuilder builder = ok();
		return builder.body(result);
	}

	public static <T> Msg<T> ok(String msg) {
		Msg.BodyBuilder builder = ok();
		return builder.msg(msg).build();
	}

	public static <T> Msg<T> ok(String msg, T result) {
		Msg.BodyBuilder builder = ok();
		return builder.msg(msg).body(result);
	}

	public static Msg.BodyBuilder fail() {
		return status(ERROR_CODE);
	}

	public static <T> Msg<T> fail(T result) {
		Msg.BodyBuilder builder = fail();
		return builder.body(result);
	}

	public static <T> Msg<T>  fail(String msg) {
		Msg.BodyBuilder builder = fail();
		return builder.msg(msg).build();
	}

	public static <T> Msg<T>  fail(String msg, T result) {
		Msg.BodyBuilder builder = fail();
		return builder.msg(msg).body(result);
	}

	private static class DefaultBuilder implements Msg.BodyBuilder {
		private final int code;
		private String message;

		public DefaultBuilder(int code) {
			this.code = code;
		}

		public DefaultBuilder(int code, String message) {
			this.code = code;
			this.message = message;
		}

		public <T> Msg<T> body(T result) {
			return new Msg(this.code, this.message, result == null ? new Object() : result);
		}

		@Override
		public Msg.BodyBuilder msg(String message) {
			return new DefaultBuilder(this.code, message);
		}

		@Override
		public <T> Msg<T> build() {
			return new Msg(this.code, this.message, null);
		}
	}

	public interface BodyBuilder {

		<T> Msg<T> body(T var1);

		Msg.BodyBuilder msg(String message);

		<T> Msg<T> build();
	}

}
