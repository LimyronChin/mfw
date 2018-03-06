package onight.scala.test;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import onight.tfw.outils.bean.BeanFieldInfo;
import onight.tfw.outils.bean.JsonPBUtil;
import onight.tfw.outils.serialize.TransBeanSerializer;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.MapEntry;
import com.google.protobuf.Message;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;

@Slf4j
public class BeanPBUtil {

	private HashMap<Class, HashMap<String, BeanFieldInfo>> beanFields = new HashMap<>();

	public String field2PBName(Field field) {
		StringBuffer sb = new StringBuffer();
		for (char ch : field.getName().toCharArray()) {
			if (ch >= 'A' && ch <= 'Z') {
				sb.append('_').append((char) (ch - 'A' + 'a'));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	public HashMap<String, BeanFieldInfo> extractMethods(Class<?> clazz) {
		HashMap<String, BeanFieldInfo> props = beanFields.get(clazz);
		if (props == null && clazz.getClassLoader() != null) {
			String uniqueName = clazz.getClassLoader().getClass().getName() + clazz.getName();
			synchronized (uniqueName.intern()) {
				props = beanFields.get(clazz);
				if (props == null) {
					props = new HashMap<>();
					for (Field field : TransBeanSerializer.allDeclaredField(clazz)) {
						if (Modifier.isTransient(field.getModifiers())) {
							continue;
						}
						PropertyDescriptor pd;
						try {
							pd = new PropertyDescriptor(field.getName(), clazz);
							BeanFieldInfo bfi = new BeanFieldInfo(field.getName(), pd.getReadMethod(), pd.getWriteMethod(), pd.getPropertyType(),
									TransBeanSerializer.isBaseType(pd.getPropertyType()), field);
							props.put(field2PBName(field), bfi);
							props.put(field.getName(), bfi);

						} catch (IntrospectionException e) {
							log.warn("cannot init BeanProp:for class=" + clazz + ",field=" + field.getName());
						}
					}
					beanFields.put(clazz, props);
				}
			}
		}
		return props;
	}

	public Object pbValue2Java(Object obj, Class dstClass) {
		if (obj instanceof Message) {
			try {
				return copyFromPB((Message) obj, dstClass.newInstance());
			} catch (Exception e) {
			}
		} else if (obj instanceof EnumValueDescriptor) {
			EnumValueDescriptor evd = (EnumValueDescriptor) obj;
			return evd.getNumber();
		}
		System.out.println("::obj=" + obj + ",dstclass=" + dstClass + ",objclas=" + obj.getClass());
		return obj;
	}

	public <T> T copyFromPB(Message fromMsg, T dst) {
		HashMap<String, BeanFieldInfo> bfis = extractMethods(dst.getClass());
		System.out.println("bfis==:" + bfis.keySet());
		if (bfis != null) {
			for (Entry<FieldDescriptor, Object> fv : fromMsg.getAllFields().entrySet()) {
				BeanFieldInfo bf = bfis.get(fv.getKey().getName());
				System.out.println("field==" + fv.getKey().getName());
				if (bf != null) {
					try {
						if (fv.getValue() instanceof List) {
							List list = (List) fv.getValue();
							if (list.size() > 0) {
								if (list.get(0) instanceof MapEntry) {
									Map<Object, Object> map = (Map<Object, Object>) bf.getFieldType().newInstance();
									System.out.println("class:" + bf.getField().getGenericType());
									ParameterizedType parameterizedType = (ParameterizedType) ((bf.getField().getGenericType()));
									for (MapEntry entry : (List<MapEntry>) fv.getValue()) {
										System.out.println("ccc:" + entry.getValue().getClass());
										map.put(pbValue2Java(entry.getKey(), (Class) parameterizedType.getActualTypeArguments()[0]),
												pbValue2Java(entry.getValue(), (Class) parameterizedType.getActualTypeArguments()[1]));
									}
									bf.getSetM().invoke(dst, map);
								} else {
									ArrayList olist = new ArrayList();
									ParameterizedType parameterizedType = (ParameterizedType) ((bf.getField().getGenericType()));
									// System.out.println("atype:"+);
									for (Object obj : list) {
										olist.add(pbValue2Java(obj, (Class) parameterizedType.getActualTypeArguments()[0]));
									}
									bf.getSetM().invoke(dst, olist);
								}
							}
						} else {
							bf.getSetM().invoke(dst, pbValue2Java(fv.getValue(), bf.getField().getType()));
						}
					} catch (Exception e) {
						log.debug("cannot invoke SetMethod:for class=" + dst.getClass() + ",field=" + bf.getFieldName() + "," + fv.getValue().getClass(), e);
					}
				}
			}
		}
		return dst;
	}

	static ObjectMapper mapper = new ObjectMapper();
	static {
		mapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(SerializationConfig.Feature.WRITE_NULL_PROPERTIES, false);
	}

	public Object getValue(FieldDescriptor fd, JsonNode node, Message.Builder builder) {
		if (fd.isRepeated() && node.isArray()) {
			// if (node.isArray()) {
			if (fd.isMapField()) {
				System.out.println("mapField::");
			}
			Iterator<JsonNode> it = (Iterator<JsonNode>) node.iterator();
			Message.Builder subbuilder = null;
			while (it.hasNext()) {
				JsonNode itnode = it.next();
				Object v = null;
				if (fd.getJavaType().equals(JavaType.MESSAGE)) {
					subbuilder = builder.newBuilderForField(fd);
					json2PB(itnode, subbuilder);
					v = subbuilder.build();
				} else {
					subbuilder = builder;
					v = getValue(fd, itnode, subbuilder);
				}
				if (v != null) {
					builder.addRepeatedField(fd, v);
				} else if (builder != null) {
					builder.addRepeatedField(fd, subbuilder.build());
				}
			}
			// } else {
			// System.out.println("hashmap!!!!!" + fd);
			//
			// }
			return null;
		}
		if (fd.getJavaType().equals(JavaType.STRING)) {
			return node.asText();
		}
		if (fd.getJavaType().equals(JavaType.INT)) {
			return node.asInt();
		}
		if (fd.getJavaType().equals(JavaType.DOUBLE)) {
			return node.asDouble();
		}
		if (fd.getJavaType().equals(JavaType.FLOAT)) {
			return node.asDouble();
		}
		if (fd.getJavaType().equals(JavaType.LONG)) {
			return node.asLong();
		}
		if (fd.getJavaType().equals(JavaType.BOOLEAN)) {
			return node.asBoolean();
		}
		if (fd.getJavaType().equals(JavaType.ENUM)) {
			EnumValueDescriptor evd = fd.getEnumType().findValueByNumber(node.asInt());
			return evd;
		}
		if (fd.getJavaType().equals(JavaType.MESSAGE)) {
			// System.out.println("message.file=" + fd);
			Message.Builder subbuilder = null;
			if (fd.isMapField()) {
				System.out.println("mapField");
				json2PBMap(fd, node, builder);
				return null;
			} else {
				subbuilder = builder.newBuilderForField(fd);
				json2PB(node, subbuilder);
				return subbuilder.build();
			}
		}
		return null;
	}

	public void json2PB(String jsonTxt, Message.Builder msgBuilder) {

		try {
			JsonNode tree = mapper.readTree(jsonTxt);
			json2PB(tree, msgBuilder);
		} catch (Exception e) {
			log.warn("error in json2PB:jsonTxt=" + jsonTxt + ",builder=" + msgBuilder, e);
		}
	}

	public void json2PBMap(FieldDescriptor fd, JsonNode node, Message.Builder msgBuilder) {
		Iterator<Map.Entry<String, JsonNode>> it = (Iterator<Map.Entry<String, JsonNode>>) node.getFields();
		while (it.hasNext()) {
			Map.Entry<String, JsonNode> item = it.next();
			MapEntry.Builder mb = (MapEntry.Builder) msgBuilder.newBuilderForField(fd);
			FieldDescriptor fd2 = mb.getDescriptorForType().getFields().get(1);
			mb.setKey(item.getKey());
			mb.setValue(getValue(fd2, item.getValue(), mb));
			msgBuilder.addRepeatedField(fd, mb.build());
		}
	}

	public void json2PB(JsonNode tree, Message.Builder msgBuilder) {
		try {
			List<FieldDescriptor> fds = msgBuilder.getDescriptorForType().getFields();
			System.out.println("tree==" + tree);
			System.out.println("fds==" + fds);

			for (FieldDescriptor fd : fds) {
				JsonNode node = tree.get(fd.getName());
				if (node == null)
					continue;
				System.out.println("fd.name=" + fd.getName() + ",javatype=" + fd.getJavaType());
				Object v = getValue(fd, node, msgBuilder);
				if (v != null) {
					msgBuilder.setField(fd, v);
					// } else {
					// System.out.println("v==null:" + fd + ",node=" + node);
				}
			}
		} catch (Exception e) {
			throw e;
		}
	}

	public <T> T toPB(Message.Builder msgBuilder, Object src) {
		HashMap<String, BeanFieldInfo> bfis = extractMethods(src.getClass());
		if (bfis != null) {
			for (Entry<String, BeanFieldInfo> bf : bfis.entrySet()) {
				Object v = null;
				try {
					v = bf.getValue().getGetM().invoke(src, null);
				} catch (Exception e) {
					log.debug("cannot invoke getMethod:for class=" + src.getClass() + ",field=" + bf.getKey());
				}
				if (v != null) {
					FieldDescriptor fd = msgBuilder.getDescriptorForType().findFieldByName(bf.getKey());
					if (fd == null)
						continue;
					try {
						if (fd.isRepeated()) {
							if (v instanceof List) {
								Message.Builder subbuilder = null;
								for (Object lv : (List) v) {
									Object pv = null;
									if (fd.getJavaType().equals(JavaType.MESSAGE)) {
										subbuilder = msgBuilder.newBuilderForField(fd);
										toPB(subbuilder, lv);
										pv = subbuilder.build();
									} else {
										pv = lv;
									}
									if (v != null) {
										msgBuilder.addRepeatedField(fd, pv);
									} else if (subbuilder != null) {
										msgBuilder.addRepeatedField(fd, subbuilder.build());
									}
								}
							} else if (v instanceof Map) {
								for (Map.Entry item : (Set<Map.Entry>) ((Map) v).entrySet()) {
									System.out.println("item==" + item + ",fd=" + fd);
									MapEntry.Builder mb = (MapEntry.Builder) msgBuilder.newBuilderForField(fd);
									FieldDescriptor fd2 = mb.getDescriptorForType().getFields().get(1);
									mb.setKey(item.getKey());
									if (fd2.getJavaType() == JavaType.MESSAGE) {
										mb.setValue(toPB(mb.newBuilderForField(fd2), item.getValue()));
									} else {
										mb.setValue(item.getValue());
									}
									msgBuilder.addRepeatedField(fd, mb.build());
									System.out.println(msgBuilder.build());
								}
							}
						} else if (fd.getJavaType() == JavaType.MESSAGE) {
							Message.Builder subbuilder = msgBuilder.newBuilderForField(fd);
							toPB(subbuilder, v);
							Object pv = subbuilder.build();
							msgBuilder.setField(fd, pv);
						} else if (fd.getJavaType() == JavaType.ENUM) {
							EnumValueDescriptor evd = fd.getEnumType().findValueByNumber((int)v);
							msgBuilder.setField(fd, evd);

							
						} else {
							msgBuilder.setField(fd, v);
						}
					} catch (Exception e) {
						if (fd.getType() == Type.STRING) {
							try {
								msgBuilder.setField(fd, v.toString());
							} catch (Exception e1) {
								log.debug("cannot invoke setfield class=" + src.getClass() + ",field=" + bf.getKey() + ",fd=" + fd + ",v=" + v);
							}
						}
					}
				}
			}
			return (T) msgBuilder.build();
		}
		return (T) msgBuilder.build();
	}
}
