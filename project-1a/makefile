all: perfect noisy

perfect: compile  
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator Perfect Parity transmit.txt

noisy: compile  
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator LowNoise Parity transmit.txt

clean:
	rm *.class

compile:
	javac *.java