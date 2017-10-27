package entity;

import lombok.AddField;
import lombok.Data;
import lombok.FieldEnum;

@AddField(value = { FieldEnum.ID })
public class B {

	public void insert(){}

	public static class VO {
	}
}
