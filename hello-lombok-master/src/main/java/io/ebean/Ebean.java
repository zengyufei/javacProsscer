package io.ebean;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by admin on 2017/9/28.
 */
public class Ebean {

	public static void insert(Object obj){}
	public static int delete(Class<?> beanType, Object id){return 0;}
	public static <T> List<T> find(Class<T> beanType) {
		return new ArrayList<>();
	}

}

class Query<T> {
	List<T> findList() {
		return Lists.newArrayList();
	}
}
