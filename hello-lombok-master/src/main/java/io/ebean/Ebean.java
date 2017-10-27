package io.ebean;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by admin on 2017/9/28.
 */
public class Ebean {

	public static void insert(Object obj){}
	public static void update(Object obj){}
	public static int delete(Class<?> beanType, Object id){return 0;}
	public static <T> Query<T> find(Class<T> beanType) {
		return new Query();
	}
	public static <T> T find(Class<T> beanType, Object id) {
		try {
			return beanType.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

}
