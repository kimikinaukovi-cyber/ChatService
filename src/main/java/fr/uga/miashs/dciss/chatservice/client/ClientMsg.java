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

package fr.uga.miashs.dciss.chatservice.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import fr.uga.miashs.dciss.chatservice.common.MessageType;
import fr.uga.miashs.dciss.chatservice.common.Packet;

/**
 * Manages the connection to a ServerMsg. Method startSession() is used to
 * establish the connection. Then messages can be send by a call to sendPacket.
 * The reception is done asynchronously (internally by the method receiveLoop())
 * and the reception of a message is notified to MessagesListeners. To register
 * a MessageListener, the method addMessageListener has to be called. Session
 * are closed thanks to the method closeSession().
 */
public class ClientMsg {

	private String serverAddress;
	private int serverPort;

	private Socket s;
	private DataOutputStream dos;
	private DataInputStream dis;

	private int identifier;

	private List<MessageListener> mListeners;
	private List<ConnectionListener> cListeners;
	
	private Set<Integer> groups = new HashSet<>();

	/**
	 * Create a client with an existing id, that will connect to the server at the
	 * given address and port
	 * 
	 * @param id      The client id
	 * @param address The server address or hostname
	 * @param port    The port number
	 */
	public ClientMsg(int id, String address, int port) {
		if (id < 0)
			throw new IllegalArgumentException("id must not be less than 0");
		if (port <= 0)
			throw new IllegalArgumentException("Server port must be greater than 0");
		serverAddress = address;
		serverPort = port;
		identifier = id;
		mListeners = new ArrayList<>();
		cListeners = new ArrayList<>();
	}

	/**
	 * Create a client without id, the server will provide an id during the the
	 * session start
	 * 
	 * @param address The server address or hostname
	 * @param port    The port number
	 */
	public ClientMsg(String address, int port) {
		this(0, address, port);
	}

	/**
	 * Register a MessageListener to the client. It will be notified each time a
	 * message is received.
	 * 
	 * @param l
	 */
	public void addMessageListener(MessageListener l) {
		if (l != null)
			mListeners.add(l);
	}
	protected void notifyMessageListeners(Packet p) {
		mListeners.forEach(x -> x.messageReceived(p));
	}
	
	/**
	 * Register a ConnectionListener to the client. It will be notified if the connection  start or ends.
	 * 
	 * @param l
	 */
	public void addConnectionListener(ConnectionListener l) {
		if (l != null)
			cListeners.add(l);
	}
	protected void notifyConnectionListeners(boolean active) {
		cListeners.forEach(x -> x.connectionEvent(active));
	}
	
	public void addGroup(int groupId) {
		groups.add(groupId);
	}
	
	public Set<Integer> getGroups() {
	    return groups;
	}


	public int getIdentifier() {
		return identifier;
	}

	/**
	 * Method to be called to establish the connection.
	 * 
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void startSession() throws UnknownHostException {
		if (s == null || s.isClosed()) {
			try {
				s = new Socket(serverAddress, serverPort);
				dos = new DataOutputStream(s.getOutputStream());
				dis = new DataInputStream(s.getInputStream());
				dos.writeInt(identifier);
				dos.flush();
				if (identifier == 0) {
					identifier = dis.readInt();
				}
				// start the receive loop
				new Thread(() -> receiveLoop()).start();
				notifyConnectionListeners(true);
			} catch (IOException e) {
				e.printStackTrace();
				// error, close session
				closeSession();
			}
		}
	}

	/**
	 * Send a packet to the specified destination (etiher a userId or groupId)
	 * 
	 * @param destId the destinatiion id
	 * @param data   the data to be sent
	 */
	public void sendPacket(int destId, byte[] data) {
		System.out.println("DEBUG CLIENT SEND dest=" + destId + " size=" + data.length);
		try {
			synchronized (dos) {
				dos.writeInt(destId);
				dos.writeInt(data.length);
				dos.write(data);
				dos.flush();
			}
		} catch (IOException e) {
			// error, connection closed
			closeSession();
		}
		
	}

	/**
	 * Start the receive loop. Has to be called only once.
	 */
	private void receiveLoop() {
		try {
			while (s != null && !s.isClosed()) {
				
				int typeOrdinal = dis.readInt();
				int sender = dis.readInt();
				int dest = dis.readInt();
				int length = dis.readInt();
				byte[] data = new byte[length];
				MessageType type = MessageType.values()[typeOrdinal];
				dis.readFully(data);
				notifyMessageListeners(new Packet(type, sender, dest, data));

			}
		} catch (IOException e) {
			// error, connection closed
		}
		closeSession();
	}

	public void closeSession() {
		try {
			if (s != null)
				s.close();
		} catch (IOException e) {
		}
		s = null;
		notifyConnectionListeners(false);
	}

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		ClientMsg c = new ClientMsg("localhost", 1666);

		// add a dummy listener that print the content of message as a string
		c.addMessageListener(p -> {
		    if (p.type == MessageType.INFO) {
		        String msg = new String(p.data);

		        if (msg.startsWith("GROUP_CREATED:")) {
		            int groupId = Integer.parseInt(msg.split(":")[1]);
		            c.addGroup(groupId);
		            System.out.println("Ajouté au groupe " + groupId);
		            
		        } else if (msg.startsWith("ADDED_TO_GROUP:")) {
		            int groupId = Integer.parseInt(msg.split(":")[1]);
		            c.addGroup(groupId);
		            System.out.println("Ajouté au groupe " + groupId);
		            
		        } else if (msg.startsWith("REMOVED_FROM_GROUP:")) {
		            int groupId = Integer.parseInt(msg.split(":")[1]);
		            c.getGroups().remove(groupId);
		            System.out.println("Retiré du groupe " + groupId);
		            
		        } else if (msg.startsWith("GROUP_DELETED:")) {
		            int groupId = Integer.parseInt(msg.split(":")[1]);
		            c.getGroups().remove(groupId);
		            System.out.println("Groupe supprimé " + groupId);
		        }
		        
		    } else if (p.destId < 0) {
		        System.out.println("[GROUPE " + p.destId + "] " + new String(p.data));
		    } else {
		        System.out.println("[USER " + p.srcId + "] " + new String(p.data));
		    }
		});
		
		// add a connection listener that exit application when connection closed
		c.addConnectionListener(active ->  {if (!active) System.exit(0);});

		c.startSession();
		System.out.println("Vous êtes : " + c.getIdentifier());

		// Thread.sleep(5000);
		

		Scanner sc = new Scanner(System.in);
		String lu = null;
		while (!"/quit".equals(lu)) {
			System.out.println("Commande ? (/msg, /gmsg, /group, /groups, /add, /remove, /delete, /quit)");
			lu = sc.nextLine();
			try {
				// MESSAGE
		        if (lu.equals("/msg")) {

		            System.out.println("Destinataire (id) ?");
		            int dest = Integer.parseInt(sc.nextLine());

		            System.out.println("Message ?");
		            String msg = sc.nextLine();

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.TEXT.ordinal());
		            dos2.write(msg.getBytes());

		            dos2.flush();

		            c.sendPacket(dest, bos.toByteArray());
		        }
		        
		        // GROUP MESSAGE
		        else if (lu.equals("/gmsg")) {

		            System.out.println("ID groupe ?");
		            int groupId = Integer.parseInt(sc.nextLine());

		            System.out.println("Message ?");
		            String msg = sc.nextLine();

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.TEXT.ordinal());
		            dos2.write(msg.getBytes());

		            dos2.flush();

		            c.sendPacket(groupId, bos.toByteArray());
		        }

		        // CREATE GROUP
		        else if (lu.equals("/group")) {

		            System.out.println("Combien de membres ?");
		            int nb = Integer.parseInt(sc.nextLine());

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.CREATE_GROUP.ordinal());
		            dos2.writeInt(nb);

		            for (int i = 0; i < nb; i++) {
		                System.out.println("ID membre " + (i + 1) + " ?");
		                dos2.writeInt(Integer.parseInt(sc.nextLine()));
		            }

		            dos2.flush();

		            c.sendPacket(0, bos.toByteArray());
		            System.out.println("Demande de création envoyée !");
		        }
		        
		        // SHOW GROUPS
		        else if (lu.equals("/groups")) {
		            System.out.println("Mes groupes : " + c.getGroups());
		        }

		        // ADD USER
		        else if (lu.equals("/add")) {

		            System.out.println("ID du groupe ?");
		            int groupId = Integer.parseInt(sc.nextLine());

		            System.out.println("ID utilisateur à ajouter ?");
		            int userId = Integer.parseInt(sc.nextLine());

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.ADD_USER_TO_GROUP.ordinal());
		            dos2.writeInt(groupId);
		            dos2.writeInt(userId);

		            dos2.flush();

		            c.sendPacket(0, bos.toByteArray());
		            System.out.println("Ajout envoyé !");
		        } 
		        
		        else if (lu.equals("/remove")) {

		            System.out.println("ID du groupe ?");
		            int groupId = Integer.parseInt(sc.nextLine());

		            System.out.println("ID utilisateur à retirer ?");
		            int userId = Integer.parseInt(sc.nextLine());

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.REMOVE_USER_FROM_GROUP.ordinal());
		            dos2.writeInt(groupId);
		            dos2.writeInt(userId);

		            dos2.flush();

		            c.sendPacket(0, bos.toByteArray());
		            System.out.println("Suppression envoyée !");
		        }
		        
		        else if (lu.equals("/delete")) {

		            System.out.println("ID du groupe ?");
		            int groupId = Integer.parseInt(sc.nextLine());

		            ByteArrayOutputStream bos = new ByteArrayOutputStream();
		            DataOutputStream dos2 = new DataOutputStream(bos);

		            dos2.writeByte(MessageType.DELETE_GROUP.ordinal());
		            dos2.writeInt(groupId);

		            dos2.flush();

		            c.sendPacket(0, bos.toByteArray());
		            System.out.println("Suppression du groupe envoyée !");
		        }

		    } catch (Exception e) {
		        System.out.println("Erreur: mauvais format");
		    }

		}

		/*
		 * int id =1+(c.getIdentifier()-1) % 2; System.out.println("send to "+id);
		 * c.sendPacket(id, "bonjour".getBytes());
		 * 
		 * 
		 * Thread.sleep(10000);
		 */

		c.closeSession();

	}

}
