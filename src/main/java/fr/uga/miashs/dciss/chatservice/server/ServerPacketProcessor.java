/*
 * Copyright (c) 2024.  Jerome David. Univ. Grenoble Alpes.
 * This file is part of DcissChatService.
 *
 * DcissChatService is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * DcissChatService is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */

package fr.uga.miashs.dciss.chatservice.server;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import fr.uga.miashs.dciss.chatservice.common.MessageType;

import fr.uga.miashs.dciss.chatservice.common.Packet;

public class ServerPacketProcessor implements PacketProcessor {
	private final static Logger LOG = Logger.getLogger(ServerPacketProcessor.class.getName());
	private ServerMsg server;

	public ServerPacketProcessor(ServerMsg s) {
		this.server = s;
	}

	@Override
	public void process(Packet p) {
		System.out.println("DEBUG: ServerPacketProcessor appelé");
		// ByteBufferVersion. On aurait pu utiliser un ByteArrayInputStream + DataInputStream à la place
		ByteBuffer buf = ByteBuffer.wrap(p.data);
		MessageType type = MessageType.values()[buf.get()];
		System.out.println("DEBUG type reçu = " + type);
		
		switch (type) { // cas creation de groupe
			case TEXT:
				handleText(p);
				break;
				
			case CREATE_GROUP:
				createGroup(p.srcId, buf);
				break;
				
			case ADD_USER_TO_GROUP:
				addUserToGroup(p.srcId, buf);
				break;
				
			case REMOVE_USER_FROM_GROUP:
		        removeUserFromGroup(p.srcId, buf);
		        break;
			case DELETE_GROUP:
		        deleteGroup(p, buf);
		        break;
			default:
				LOG.warning("Server message of type=" + type + " not handled by procesor");
		}
	}
	
	public void handleText(Packet p) {
		int destId = p.destId;
		
		if (destId > 0) {
			// Cas USER -> USER
			sendToUser(p.srcId, destId, p.data);
		} else {
			// Cas GROUP
			sendToGroup(p.srcId, destId, p.data);
		}
	}
	private void sendToUser(int src, int dest, byte[] data) {
	    Packet out = new Packet(MessageType.TEXT, src, dest, data);
	    server.sendToClient(dest, out);
	}

	private void sendToGroup(int src, int groupId, byte[] data) {
	    Packet out = new Packet(MessageType.TEXT, src, groupId, data);
	    server.sendToGroup(src, groupId, out);
	}
	
	public void createGroup(int ownerId, ByteBuffer data) {
		System.out.println("DEBUG: createGroup appelé par user " + ownerId);
		int nb = data.getInt();
		System.out.println("DEBUG: nb membres = " + nb);
		GroupMsg g = server.createGroup(ownerId);
		System.out.println("DEBUG: groupe créé = " + g.getId());
		notifyGroupCreated(g, ownerId);
		
		for (int i = 0; i < nb; i++) {
			int memberId = data.getInt();
			System.out.println("DEBUG: ajout membre " + memberId);
			UserMsg u = server.getUser(memberId);
			if (u != null) {
				g.addMember(u);
				notifyGroupCreated(g, memberId);
			}
		}
	}
	
	private void notifyGroupCreated(GroupMsg g, int userId) {
		String msg = "GROUP_CREATED:" + g.getId();
		
		Packet p = new Packet(
			MessageType.INFO,
			0,
			userId,
			msg.getBytes()
		);
		server.sendToClient(userId, p);
	}
	
	public void addUserToGroup(int requesterId, ByteBuffer buf) {
		int groupId = buf.getInt();
		int userId = buf.getInt();
		
		GroupMsg g = server.getGroup(groupId);
		UserMsg u = server.getUser(userId);
		
		if (g.getOwner().getId() != requesterId) return;
		if (g != null && u != null) {
			g.addMember(u);
			String msg = "ADDED_TO_GROUP:" + groupId;
			Packet info = new Packet(MessageType.INFO, 0, userId, msg.getBytes());
			server.sendToClient(userId, info);
		}	
	}
	
	public void removeUserFromGroup(int requesterId, ByteBuffer buf) {
	    int groupId = buf.getInt();
	    int userId = buf.getInt();

	    GroupMsg g = server.getGroup(groupId);
	    UserMsg u = server.getUser(userId);

	    if (g != null && u != null) {
	        g.removeMember(u);

	        // notif au user supprimé
	        String msg = "REMOVED_FROM_GROUP:" + groupId;
	        Packet info = new Packet(MessageType.INFO, 0, userId, msg.getBytes());
	        server.sendToClient(userId, info);
	    }
	}
	
	public void deleteGroup(Packet p, ByteBuffer buf) {
	    int groupId = buf.getInt();

	    GroupMsg g = server.getGroup(groupId);
	    if (g == null) return;

	    // 🔥 notifier tous les membres AVANT suppression
	    for (UserMsg u : g.getMembers()) {
	        String msg = "GROUP_DELETED:" + groupId;
	        Packet info = new Packet(MessageType.INFO, 0, u.getId(), msg.getBytes());
	        server.sendToClient(u.getId(), info);
	    }

	    server.removeGroup(groupId);
	}

}
