package onight.osgi.otransio.nio;

import onight.tfw.otransio.api.beans.FramePacket;

import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Encoder extends AbstractTransformer<FramePacket, Buffer> {

	Logger log = LoggerFactory.getLogger(Encoder.class);

	@Override
	public String getName() {
		return "";
	}

	@Override
	public boolean hasInputRemaining(AttributeStorage storage, FramePacket input) {
		return input != null;
	}

	// Buffer HB_BUFFER = HeapBuffer.wrap(new byte[] { '0', '0', '0', '2', '\n',
	// '\n' });

	@Override
	protected TransformationResult<FramePacket, Buffer> transformImpl(AttributeStorage storage, FramePacket input) throws TransformationException {
		byte[] bodyb = input.genBodyBytes();
		byte[] extb = input.genExtBytes(); 
		input.getFixHead().setExtsize(extb.length);
		input.getFixHead().setBodysize(bodyb.length);
		Buffer output = obtainMemoryManager(storage).allocate(16 + input.getFixHead().getTotalSize());
		output.put(input.getFixHead().genBytes());
		if (extb.length > 0) {
			output.put(extb);
		}
		if (bodyb.length > 0) {
			output.put(bodyb);
		}
		output.flip();
		output.allowBufferDispose(true);
		log.trace("encode:"+input.getFixHead().toStrHead()+",extsize="+extb.length+",bodysize="+bodyb.length);
		return TransformationResult.createCompletedResult(output, null);
	}
}
