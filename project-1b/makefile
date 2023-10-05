all: perfect noisy

perfect: compile  
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator Perfect Dumb transmit.txt

parity: compile  
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator LowNoise Parity transmit.txt

crc: compile
	java Simulator LowNoise CRC transmit.txt


clean:
	rm *.class

compile:
	javac *.java