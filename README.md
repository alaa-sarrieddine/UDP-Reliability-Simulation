The UDP Reliability Simulator is a Java-based application designed to simulate unreliable network conditions in a packet-switched communication scenario using the UDP protocol. This simulation replicates real-world network challenges such as packet loss, delivery delays, and out-of-order packet reception, providing a controlled environment for testing network reliability and behavior under adverse conditions.

Objective
To emulate an unreliable communication channel between two end-users, simulating:

Random packet loss with a user-defined probability.
Randomized packet delivery delays within a specified range.
Comprehensive tracking of packet delivery statistics, including losses, delays, and average delay times.
The simulator serves as both an educational tool and a testing framework for understanding and handling the inherent unreliability of the UDP protocol in network communications.

Key Features
Packet Loss Simulation
Implements a probabilistic loss mechanism controlled via a user-defined loss rate.
Delivery Delay Simulation
Introduces random delays within a configurable range.
Two-Way Communication
Supports bidirectional communication between two users (A and B).
Statistical Reporting
Provides detailed statistics on packet losses, delays, and average delivery times for each communication direction.
Configurable Parameters
Enables customization of loss rate, minimum/maximum delay, and server port via command-line arguments.
Additional Bonus Features (Optional)
Variable packet sizes and messages.
Advanced loss and delay patterns (e.g., bursts, Gaussian distributions).
Support for concurrent multi-user connections with multithreading.
Usage
Configure and run the UnreliableChannel.java server.
Launch client applications for Users A and B to initiate communication.
Observe real-time simulation and results in the server console.
