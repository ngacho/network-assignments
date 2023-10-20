all: par

par: compile  
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator LowNoise PAR transmit.txt

perfect : compile
	echo "simulating a transmission of data through a Perfect medium\n"
	java Simulator Perfect PAR transmit.txt


clean:
	rm *.class

compile:
	javac *.java