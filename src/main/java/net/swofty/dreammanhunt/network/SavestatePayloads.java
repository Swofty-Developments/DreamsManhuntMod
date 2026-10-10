package net.swofty.dreammanhunt.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.swofty.dreammanhunt.DreamManhunt;
import net.swofty.dreammanhunt.api.SavestateInfo;

import java.util.List;

public final class SavestatePayloads {
	public static final int MAX_NAME_LENGTH = 64;
	private static final int MAX_ID_LENGTH = 64;
	private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(MAX_ID_LENGTH);

	private SavestatePayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DreamManhunt.MOD_ID, path));
	}

	public record SaveRequest(String name) implements CustomPacketPayload {
		public static final Type<SaveRequest> TYPE = payloadType("save_request");
		public static final StreamCodec<ByteBuf, SaveRequest> CODEC =
				ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).map(SaveRequest::new, SaveRequest::name);

		@Override
		public Type<SaveRequest> type() {
			return TYPE;
		}
	}

	public record ListRequest() implements CustomPacketPayload {
		public static final ListRequest INSTANCE = new ListRequest();
		public static final Type<ListRequest> TYPE = payloadType("list_request");
		public static final StreamCodec<ByteBuf, ListRequest> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<ListRequest> type() {
			return TYPE;
		}
	}

	public record RestoreRequest(String id) implements CustomPacketPayload {
		public static final Type<RestoreRequest> TYPE = payloadType("restore_request");
		public static final StreamCodec<ByteBuf, RestoreRequest> CODEC = ID_CODEC.map(RestoreRequest::new, RestoreRequest::id);

		@Override
		public Type<RestoreRequest> type() {
			return TYPE;
		}
	}

	public record DeleteRequest(String id) implements CustomPacketPayload {
		public static final Type<DeleteRequest> TYPE = payloadType("delete_request");
		public static final StreamCodec<ByteBuf, DeleteRequest> CODEC = ID_CODEC.map(DeleteRequest::new, DeleteRequest::id);

		@Override
		public Type<DeleteRequest> type() {
			return TYPE;
		}
	}

	public record RestoreReady(String id) implements CustomPacketPayload {
		public static final Type<RestoreReady> TYPE = payloadType("restore_ready");
		public static final StreamCodec<ByteBuf, RestoreReady> CODEC = ID_CODEC.map(RestoreReady::new, RestoreReady::id);

		@Override
		public Type<RestoreReady> type() {
			return TYPE;
		}
	}

	public record StateList(List<SavestateInfo> states, boolean canManage) implements CustomPacketPayload {
		public static final Type<StateList> TYPE = payloadType("state_list");
		public static final StreamCodec<ByteBuf, StateList> CODEC = StreamCodec.composite(
				SavestateInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), StateList::states,
				ByteBufCodecs.BOOL, StateList::canManage,
				StateList::new
		);

		@Override
		public Type<StateList> type() {
			return TYPE;
		}
	}

	public record Capture(String id) implements CustomPacketPayload {
		public static final Type<Capture> TYPE = payloadType("capture");
		public static final StreamCodec<ByteBuf, Capture> CODEC = ID_CODEC.map(Capture::new, Capture::id);

		@Override
		public Type<Capture> type() {
			return TYPE;
		}
	}

	public record Discard(String id) implements CustomPacketPayload {
		public static final Type<Discard> TYPE = payloadType("discard");
		public static final StreamCodec<ByteBuf, Discard> CODEC = ID_CODEC.map(Discard::new, Discard::id);

		@Override
		public Type<Discard> type() {
			return TYPE;
		}
	}

	public record RestoreBegin(String id) implements CustomPacketPayload {
		public static final Type<RestoreBegin> TYPE = payloadType("restore_begin");
		public static final StreamCodec<ByteBuf, RestoreBegin> CODEC = ID_CODEC.map(RestoreBegin::new, RestoreBegin::id);

		@Override
		public Type<RestoreBegin> type() {
			return TYPE;
		}
	}

	public record RestoreApply(String id) implements CustomPacketPayload {
		public static final Type<RestoreApply> TYPE = payloadType("restore_apply");
		public static final StreamCodec<ByteBuf, RestoreApply> CODEC = ID_CODEC.map(RestoreApply::new, RestoreApply::id);

		@Override
		public Type<RestoreApply> type() {
			return TYPE;
		}
	}

	public record RestoreResume(String id) implements CustomPacketPayload {
		public static final Type<RestoreResume> TYPE = payloadType("restore_resume");
		public static final StreamCodec<ByteBuf, RestoreResume> CODEC = ID_CODEC.map(RestoreResume::new, RestoreResume::id);

		@Override
		public Type<RestoreResume> type() {
			return TYPE;
		}
	}

	public record Notice(Component text) implements CustomPacketPayload {
		public static final Type<Notice> TYPE = payloadType("notice");
		public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC =
				ComponentSerialization.TRUSTED_STREAM_CODEC.map(Notice::new, Notice::text);

		@Override
		public Type<Notice> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry<RegistryFriendlyByteBuf> c2s = PayloadTypeRegistry.serverboundPlay();
		c2s.register(SaveRequest.TYPE, SaveRequest.CODEC);
		c2s.register(ListRequest.TYPE, ListRequest.CODEC);
		c2s.register(RestoreRequest.TYPE, RestoreRequest.CODEC);
		c2s.register(DeleteRequest.TYPE, DeleteRequest.CODEC);
		c2s.register(RestoreReady.TYPE, RestoreReady.CODEC);

		PayloadTypeRegistry<RegistryFriendlyByteBuf> s2c = PayloadTypeRegistry.clientboundPlay();
		s2c.registerLarge(StateList.TYPE, StateList.CODEC, 8 * 1024 * 1024);
		s2c.register(Capture.TYPE, Capture.CODEC);
		s2c.register(Discard.TYPE, Discard.CODEC);
		s2c.register(RestoreBegin.TYPE, RestoreBegin.CODEC);
		s2c.register(RestoreApply.TYPE, RestoreApply.CODEC);
		s2c.register(RestoreResume.TYPE, RestoreResume.CODEC);
		s2c.register(Notice.TYPE, Notice.CODEC);
	}
}
