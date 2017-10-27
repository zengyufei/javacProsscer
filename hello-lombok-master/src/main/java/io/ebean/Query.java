package io.ebean;

import com.google.common.collect.Lists;

import java.util.List;

public class Query<T> {
	public List<T> findList() {
		return Lists.newArrayList();
	}

	public Query<T> setIncludeSoftDeletes() {
		return this;
	}

	public Query<T> setId(Object id) {
		return this;
	}

	public Query<T> where() {
		return this;
	}

	public Query<T> idIn(Object ids) {
		return this;
	}

	public T findUnique() {
		return (T) new Object();
	}
}