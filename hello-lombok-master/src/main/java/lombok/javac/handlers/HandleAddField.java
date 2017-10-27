package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.AccessLevel;
import lombok.AddField;
import lombok.FieldEnum;
import lombok.core.AnnotationValues;
import lombok.core.HandlerPriority;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.Javac.CTC_INT;
import static lombok.javac.handlers.JavacHandlerUtil.*;

@ProviderFor(JavacAnnotationHandler.class)
@HandlerPriority(66560) // 2^16 + 2^10; we must run AFTER HandleVal which is at 2^16
public class HandleAddField extends JavacAnnotationHandler<AddField> {

	@Override
	public void handle(final AnnotationValues<AddField> annotation,
	                   final JCAnnotation source, final JavacNode annotationNode) {
		// 如果存在则删除
		deleteAnnotationIfNeccessary(annotationNode, AddField.class);
		// 获取该注解的地方，如果注解在类上面，则获取该类，如果注解在方法上，则获取该方法，如此类推
		JavacNode typeNode = annotationNode.up();
		JavacTreeMaker maker = typeNode.getTreeMaker();
		FieldEnum[] value = annotation.getInstance().value();
		JCAnnotation returnThisAnnotation = maker.Annotation(
				genTypeRef(typeNode, "lombok.experimental.Accessors"), List.nil());
		for (FieldEnum fieldEnum : value) {
			if (fieldEnum.equals(FieldEnum.ID)) {
				/*
				    @Id
				    @GeneratedValue(strategy = GenerationType.IDENTITY)
				    @Min(value = 1, message = "id 不能为空", groups = { QueryById.class, Update.class, DeleteById.class })
				    @NotNull(message = "id 不能为空", groups = { QueryById.class, Update.class, DeleteById.class })
				    private Long id;
				*/
				String msg = "id 不能为空";
				ListBuffer lb = new ListBuffer();
				lb.append(genTypeRef(typeNode, "lombok.QueryById.class"));
				lb.append(genTypeRef(typeNode, "lombok.Update.class"));

				JCAnnotation annotation1 = maker.Annotation(
						genTypeRef(typeNode, "javax.persistence.Id"), List.nil());
				JCAnnotation annotation2 = getNotNullAnnotation(typeNode, maker, msg, lb);
				JCAnnotation annotation3 = getMinAnnotation(typeNode, maker, 1, msg, lb);
				JCAnnotation annotation4 = getGeneratedValueAnnotation(typeNode, maker);
				List<JCAnnotation> of = List.of(returnThisAnnotation, annotation1, annotation2,
						annotation3, annotation4);

				JavacNode fieldNode = createField(typeNode, source, "id", "java.lang.Long", of);
				createGetSetMethod(annotationNode, maker, fieldNode);
			}else if(fieldEnum.equals(FieldEnum.FEATURES)) {
				/*
				    @DbJson
				    @DbComment("额外字段")
				    @Setter(value = AccessLevel.NONE)
				    @Getter(value = AccessLevel.NONE)
				    protected JSONObject features = new JSONObject();

				    public String getFeatures() {
				        return features == null || features.size() == 0 ? null : JSON.toJSONString(features, SerializerFeature.UseISO8601DateFormat);
				    }

				    public void setupFeature(String columnName, String value) {
				        features.put(columnName, value);
				    }

				    public void setupFeature(String columnName, Object value) {
				        features.put(columnName, value);
				    }

				    public void removeFeature(String columnName) {
				        features.remove(columnName);
				    }

				    public String getFeature(String columnName) {
				        return features.getString(columnName);
				    }

				    public <T> T getFeature(String columnName, Class<T> clz) {
				        return features.getObject(columnName, clz);
				    }

				    public void setFeatures(String features) {
				        this.features = StringUtils.isBlank(features) ? new JSONObject() : JSONObject.parseObject(features, Feature.AllowISO8601DateFormat);
				    }
				*/
			}
		}
	}

	private void createGetSetMethod(JavacNode annotationNode, JavacTreeMaker maker, JavacNode fieldNode) {
		new HandleGetter().createGetterForField(AccessLevel.PUBLIC,
				fieldNode, annotationNode, false, false, List.nil());
		String setterName = toSetterName(fieldNode);
		JCTree.JCMethodDecl setter = new HandleSetter().createSetter(Flags.PUBLIC, fieldNode, maker,
				setterName, true, annotationNode, List.nil(), List.nil());
		injectMethod(fieldNode.up(), setter);
	}

	private JCAnnotation getMinAnnotation(JavacNode typeNode, JavacTreeMaker maker, int value, String msg, ListBuffer lb) {
		JCTree.JCIdent valueToLeft = maker.Ident(typeNode.toName("value"));
		JCTree.JCIdent messageToLeft = maker.Ident(typeNode.toName("message"));
		JCTree.JCIdent groupsToLeft = maker.Ident(typeNode.toName("groups"));
		ListBuffer args = new ListBuffer();

		JCTree.JCLiteral valueToRight = maker.Literal(CTC_INT, value);
		JCTree.JCAssign valueEnd = maker.Assign(valueToLeft, valueToRight);
		args.append(valueEnd);

		JCTree.JCLiteral messageToRight = maker.Literal(msg);
		JCTree.JCAssign messageEnd = maker.Assign(messageToLeft, messageToRight);
		args.append(messageEnd);

		if(lb != null && !lb.isEmpty()){
			JCTree.JCNewArray groupsToRight = maker.NewArray(null, List.nil(), lb.toList());
			JCTree.JCAssign groupsEnd = maker.Assign(groupsToLeft, groupsToRight);
			args.append(groupsEnd);
		}

		return maker.Annotation(
				genTypeRef(typeNode, "javax.validation.constraints.Min"),
				args.toList());
	}

	private JCAnnotation getNotNullAnnotation(JavacNode typeNode, JavacTreeMaker maker, String msg, ListBuffer lb) {
		JCTree.JCIdent messageToLeft = maker.Ident(typeNode.toName("message"));
		JCTree.JCIdent groupsToLeft = maker.Ident(typeNode.toName("groups"));
		ListBuffer args = new ListBuffer();

		JCTree.JCLiteral messageToRight = maker.Literal(msg);
		JCTree.JCAssign messageEnd = maker.Assign(messageToLeft, messageToRight);
		args.append(messageEnd);

		if(lb != null && !lb.isEmpty()){
			JCTree.JCNewArray groupsToRight = maker.NewArray(null, List.nil(), lb.toList());
			JCTree.JCAssign groupsEnd = maker.Assign(groupsToLeft, groupsToRight);
			args.append(groupsEnd);
		}

		return maker.Annotation(
				genTypeRef(typeNode, "javax.validation.constraints.NotNull"),
				args.toList());
	}

	private JCAnnotation getGeneratedValueAnnotation(JavacNode typeNode, JavacTreeMaker maker) {
		JCTree.JCIdent strategyToLeft = maker.Ident(typeNode.toName("strategy"));
		JCTree.JCExpression generationTypeClass = genTypeRef(typeNode, "javax.persistence.GenerationType");
		JCTree.JCFieldAccess strategyToRight = maker.Select(generationTypeClass, typeNode.toName("IDENTITY"));

		JCTree.JCAssign strategyEnd = maker.Assign(strategyToLeft, strategyToRight);

		return maker.Annotation(
				genTypeRef(typeNode, "javax.persistence.GeneratedValue"),
				List.of(strategyEnd));
	}


	private static JavacNode createField(JavacNode typeNode,
	                                   JCTree source, String fieldName, String fieldType, List<JCAnnotation> annots) {
		JavacTreeMaker maker = typeNode.getTreeMaker();
		JCTree.JCExpression loggerType = chainDotsString(typeNode, fieldType);
		JCTree.JCVariableDecl node = maker.VarDef(maker.Modifiers(Flags.PRIVATE, annots),
				typeNode.toName(fieldName), loggerType, null);

		JCTree.JCVariableDecl fieldDecl = recursiveSetGeneratedBy(node, source, typeNode.getContext());

		JavacNode fieldNode = injectField(typeNode, fieldDecl);
		return fieldNode;
	}


}
