## RUDP: Reliable UDP File Transfer Protocol 📡

---

### Project Overview
This repository implements a **Reliable UDP (RUDP)** protocol from scratch, providing TCP-like reliability guarantees over the inherently unreliable UDP transport layer. Our implementation handles the core challenges of network communication: packet loss, out-of-order delivery, and connection management through custom protocol design.

The system demonstrates a complete client-server file transfer application with proper connection establishment, reliable data delivery, and graceful connection termination. Built entirely in Java, this project showcases low-level network programming and protocol implementation skills.

---

### Architecture & Design

**Core Components:**
- **RUDPSource.java**: Client implementation handling file transmission and connection initiation
- **RUDPDestination.java**: Server implementation managing file reception and connection termination  
- **Custom TCP Header**: 16-byte header structure supporting sequence numbers, acknowledgments, and control flags

**Protocol Features:**
- Three-way handshake for connection establishment
- Sequence number tracking for ordered delivery
- Acknowledgment-based reliability with retransmission
- Checksum validation for data integrity
- Four-way handshake for graceful connection closure

---

### Technical Implementation

**Reliability Mechanisms:**
- **Automatic Repeat Request (ARQ)**: Failed transmissions trigger retransmission after timeout
- **Sequence Number Management**: Proper tracking of sent and expected sequence numbers
- **Acknowledgment Processing**: Cumulative acknowledgments ensure data delivery
- **Connection State Management**: Proper handling of connection establishment and teardown phases

**Network Simulation:**
- Configurable packet loss probability (default: 50%)
- Variable network delay simulation (0-100ms range)
- Real-time statistics tracking and reporting

**Memory Management:**
- Fragment storage system for out-of-order packet handling  
- Efficient byte array manipulation for header construction
- Resource cleanup and proper socket management

---

### Usage Instructions

**Compile the project:**
```bash
javac Phase3/*.java
```

**Start the destination (server):**
```bash
java Phase3.RUDPDestination <port>
```

**Run the source (client):**
```bash
java Phase3.RUDPSource -r <host>:<port> -f <filename>
```

**Example Usage:**
```bash
# Terminal 1 - Start server
java Phase3.RUDPDestination 8080

# Terminal 2 - Send file
java Phase3.RUDPSource -r localhost:8080 -f document.txt
```

---

### Network Protocol Details

**Custom TCP Header Format:**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |        Destination Port       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                     Acknowledgment Number                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Checksum           | Header Len|     Flags         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**Connection States:**
1. **LISTEN**: Server awaiting connection requests
2. **SYN-SENT**: Client initiated connection request  
3. **SYN-RECEIVED**: Server received SYN, sent SYN-ACK
4. **ESTABLISHED**: Active data transfer phase
5. **FIN-WAIT**: Connection termination initiated

---

### Key Features

**🔄 Robust Connection Management:**
- Complete three-way handshake implementation
- Proper sequence number initialization and tracking
- Timeout-based retransmission with configurable limits

**📦 Reliable Data Transfer:**
- Segmentation of large files into manageable packets (64-byte payload)
- In-order delivery guarantee through sequence number validation
- Fragment reassembly at destination

**🛡️ Error Detection & Recovery:**
- Checksum computation for data integrity verification
- Duplicate detection and handling
- Automatic retransmission on packet loss or corruption

**📊 Network Simulation:**
- Realistic network condition modeling
- Statistical reporting on packet loss and delays
- Configurable parameters for testing different scenarios

---

### Performance Characteristics

The implementation successfully handles:
- **Variable Network Conditions**: Adapts to different loss rates and delay patterns
- **Large File Transfers**: Efficient segmentation and reassembly for files of any size
- **Concurrent Operations**: Proper state management for simultaneous connection handling
- **Resource Efficiency**: Minimal memory footprint with effective cleanup routines

---

### Development Insights

This project required deep understanding of:
- Network protocol design principles
- Java socket programming and UDP mechanics  
- Bit manipulation and byte-level data handling
- State machine implementation for connection management
- Error handling and recovery strategies in distributed systems

The implementation demonstrates proficiency in systems programming, network protocols, and reliable communication design patterns essential for distributed applications.
