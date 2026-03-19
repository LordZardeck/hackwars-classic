package game;

/**
Computer.java

Description: This class represents most of the data about a player, and handles loading/parsing/saving their XML file. It forks off incoming information
to other classes, such as ports, watches, and applications. It has the main threaded event loop that is 
used to process incoming operations from an individual player/someone interacting with their account.
This class is a beast, know it well lest ye be bitten.
*/

import assignments.DamageAssignment;
import assignments.LoginFailedAssignment;
import assignments.LoginSuccessAssignment;
import assignments.PacketAssignment;
import assignments.PacketNetwork;
import assignments.PacketPort;
import assignments.PacketWatch;
import java.util.*;

import com.hackwars.game.program.*;
import game.computer.dispatch.CommandDispatcher;
import game.computer.dispatch.CommandRegistry;
import game.computer.packet.ComputerDamagePacketBuilder;
import game.computer.packet.ComputerDamagePacketSnapshot;
import game.computer.packet.ComputerPacketBuilder;
import game.computer.packet.ComputerStandardPacketSnapshot;
import game.computer.packet.PortHealthSnapshot;
import game.computer.persistence.ComputerSnapshot;
import game.computer.persistence.XmlComputerPersistence;
import game.computer.runtime.ComputerRuntimeCoordinator;
import game.computer.runtime.RuntimeCaptchaPayload;
import game.computer.runtime.RuntimePortSnapshot;
import game.computer.runtime.RuntimeQueuedTask;
import game.computer.runtime.RuntimeTickEvent;
import game.computer.runtime.RuntimeTickState;
import game.computer.session.CaptchaChallenge;
import game.computer.session.ComputerSessionService;
import game.computer.session.LoginRequest;
import game.computer.session.PlayStatisticsRequest;
import game.computer.session.RemoteFunctionPackResult;
import org.w3c.dom.Node;
import java.util.concurrent.Semaphore;
import com.hackwars.game.functions.AddShowChoices;
import com.hackwars.game.functions.BankXP;
import com.hackwars.game.functions.ChangeWatchPort;
import com.hackwars.game.functions.CreateFolder;
import com.hackwars.game.functions.DeleteFolder;
import com.hackwars.game.functions.DeleteLogs;
import com.hackwars.game.functions.DoChallenge;
import com.hackwars.game.functions.Function;
import com.hackwars.game.functions.HttpXP;
import com.hackwars.game.functions.LaunchNetworkAttack;
import com.hackwars.game.functions.PortOnOff;
import com.hackwars.game.functions.RedirectXP;
import com.hackwars.game.functions.RepairXP;
import com.hackwars.game.functions.RequestAttackDefault;
import com.hackwars.game.functions.RequestFTPUpdate;
import com.hackwars.game.functions.RequestSave;
import com.hackwars.game.functions.RequestTask;
import com.hackwars.game.functions.RequestTrigger;
import com.hackwars.game.functions.RequestTriggerNote;
import com.hackwars.game.functions.RequestZombieAttack;
import com.hackwars.game.functions.SavePortNote;
import com.hackwars.game.functions.ScanXP;
import com.hackwars.game.functions.SetCode;
import com.hackwars.game.functions.SetDefaultPort;
import com.hackwars.game.functions.SetDummyPort;
import com.hackwars.game.functions.SetFTPPassword;
import com.hackwars.game.functions.UninstallPort;
import com.hackwars.game.functions.WatchXP;
import java.io.*;
import java.text.*;
import game.runchallenge.ChallengeRunner;
import hackscript.model.TypeBoolean;
import hackscript.model.TypeFloat;
import hackscript.model.TypeInteger;
import hackscript.model.TypeString;
import hackscript.model.Variable;
import view.Task;
import util.LoadXML;
import util.LocalWebConfig;
import util.Time;

public class Computer implements Runnable{//Runnable is an interface that allows us to make this class be a thread.
	private static String getPropertySafe(String key,String fallback){
		try{
			return System.getProperty(key,fallback);
		}catch(SecurityException e){
			return fallback;
		}
	}

	//max ops values.
	public static final int FREE_MAX_OPS = 4096;
	public static final int PAY_MAX_OPS = 16384;
	private static final boolean REMOTE_XMLRPC_ENABLED = Boolean.parseBoolean(getPropertySafe("hackwars.remoteXmlRpc", "false"));
	public int MAX_OPS = 4096;
	
	//file size limits.
	public static final int FREE_FILE_SIZE_LIMIT = 60000;
	public static final int PAY_FILE_SIZE_LIMIT = 240000;
	public int FILE_SIZE_LIMIT=60000;

    // makers (allows for selling)
    public final static HashMap makers = new HashMap();
    static {
        makers.put("Alexander", 15.0f);
        makers.put("Low", 30.0f);
        makers.put("Medium", 150.0f);
        makers.put("High", 1500.0f);
        makers.put("Rare", 15000.0f);
        makers.put("Holiday", 250.0f);
        makers.put("I", 15.0f);
        makers.put("II", 23.0f);
        makers.put("III", 36.0f);
        makers.put("IV", 56.0f);
        makers.put("V", 87.0f);
        makers.put("VI", 134.0f);
        makers.put("VII", 208.0f);
        makers.put("VIII", 322.0f);
        makers.put("IX", 500.0f);
        makers.put("X", 775.0f);
        makers.put("XI", 1201.0f);
        makers.put("XII", 1861.0f);
        makers.put("XIII", 2885.0f);
        makers.put("XIV", 4471.0f);
        makers.put("XV", 6930.0f);
        makers.put("XVI", 10742.0f);
        makers.put("XVII", 16649.0f);
        makers.put("XVIII", 25807.0f);
        makers.put("XIX", 40000.0f);
        makers.put("XX", 62000.0f);
        makers.put("Trash.I", 15.0f);
        makers.put("Trash.II", 17.0f);
        makers.put("Trash.III", 19.0f);
        makers.put("Trash.IV", 21.0f);
        makers.put("Trash.V", 24.0f);
        makers.put("Trash.VI", 27.0f);
        makers.put("Trash.VII", 30.0f);
        makers.put("Trash.VIII", 34.0f);
        makers.put("Trash.IX", 38.0f);
        makers.put("Trash.X", 43.0f);
        makers.put("Trash.XI", 49.0f);
        makers.put("Trash.XII", 55.0f);
        makers.put("Trash.XIII", 62.0f);
        makers.put("Trash.XIV", 69.0f);
        makers.put("Trash.XV", 78.0f);
        makers.put("Trash.XVI", 88.0f);
        makers.put("Trash.XVII", 99.0f);
        makers.put("Trash.XVIII", 111.0f);
        makers.put("Trash.XIX", 125.0f);
        makers.put("Trash.XX", 141.0f);
        makers.put("Trash.XXI", 158.0f);
        makers.put("Trash.XXII", 178.0f);
        makers.put("Trash.XXIII", 200.0f);
        makers.put("Trash.XXIV", 225.0f);
        makers.put("Trash.XXV", 253.0f);
        makers.put("Trash.XXVI", 285.0f);
        makers.put("Trash.XXVII", 321.0f);
        makers.put("Trash.XXVIII", 361.0f);
        makers.put("Trash.XXIX", 406.0f);
        makers.put("Trash.XXX", 457.0f);
        makers.put("Trash.XXXI", 514.0f);
        makers.put("Trash.XXXII", 578.0f);
        makers.put("Trash.XXXIII", 650.0f);
        makers.put("Trash.XXXIV", 731.0f);
        makers.put("Trash.XXXV", 823.0f);
        makers.put("Trash.XXXVI", 926.0f);
        makers.put("Trash.XXXVII", 1041.0f);
        makers.put("Trash.XXXVIII", 1171.0f);
        makers.put("Trash.XXXIX", 1318.0f);
        makers.put("Trash.XL", 1483.0f);
        makers.put("Token.Silverlight", 500.0f);
        makers.put("Token.Draconis", 250.0f);
        makers.put("Xyphex.I", 15.0f);
        makers.put("Xyphex.II", 23.0f);
        makers.put("Xyphex.III", 36.0f);
        makers.put("Xyphex.IV", 56.0f);
        makers.put("Xyphex.V", 87.0f);
        makers.put("Xyphex.VI", 134.0f);
        makers.put("Xyphex.VII", 208.0f);
        makers.put("Xyphex.VIII", 322.0f);
        makers.put("Xyphex.IX", 500.0f);
        makers.put("Xyphex.X", 775.0f);
        makers.put("Xyphex.XI", 1201.0f);
        makers.put("Xyphex.XII", 1861.0f);
        makers.put("Xyphex.XIII", 2885.0f);
        makers.put("Xyphex.XIV", 4471.0f);
        makers.put("Xyphex.XV", 6930.0f);
        makers.put("Xyphex.XVI", 10742.0f);
        makers.put("Xyphex.XVII", 16649.0f);
        makers.put("Xyphex.XVIII", 25807.0f);
        makers.put("Xyphex.XIX", 40000.0f);
        makers.put("Xyphex.XX", 62000.0f);
    }
	
	//packets that originate from the client.
	private final static HashMap clientPackets = new HashMap();
	static {
		clientPackets.put("fetchports",0);
		clientPackets.put("setdefaultport",0);
		clientPackets.put("changenetwork",0);
		clientPackets.put("healport",0);
		clientPackets.put("requestequipment",0);
		clientPackets.put("installequipment",0);
		clientPackets.put("repairequipment",1);
		clientPackets.put("fetchwatches",0);
		clientPackets.put("requestpage",1);
		clientPackets.put("requestpurchase",0);
		clientPackets.put("requesttrigger",0);
		clientPackets.put("requestsave",0);
		clientPackets.put("requesttask",0);
		clientPackets.put("requestwebpage",1);
		clientPackets.put("submit",0);
		clientPackets.put("makebounty",0);
		clientPackets.put("exit",0);
		clientPackets.put("vote",0);
		clientPackets.put("savepage",0);
		clientPackets.put("withdraw",0);
		clientPackets.put("requestdirectory",0);
		clientPackets.put("unlock",0);
		clientPackets.put("setftppassword",0);
		clientPackets.put("requestsecondarydirectory",0);
		clientPackets.put("requestcancelattack",0);
		clientPackets.put("cluedata",0);
		clientPackets.put("requestzombiecancelattack",0);
		clientPackets.put("installapplication",0);
		clientPackets.put("installwatch",0);
		clientPackets.put("setwatchobservedports",0);
		clientPackets.put("installfirewall",0);
		clientPackets.put("replaceapplication",0);
		clientPackets.put("uninstallport",0);
		clientPackets.put("portonoff",0);
		clientPackets.put("peekcode",0);
		clientPackets.put("peeklogs",0);
		clientPackets.put("saveportnote",0);
		clientPackets.put("setwatchquantity",0);
		clientPackets.put("setwatchonoff",0);
		clientPackets.put("setwatchnote",0);
		clientPackets.put("setwatchsearchfirewall",0);
		clientPackets.put("deletewatch",0);
		clientPackets.put("deletefirewall",0);
		clientPackets.put("changewatchport",0);
		clientPackets.put("changewatchtype",0);
		clientPackets.put("deletefolder",0);
		clientPackets.put("setdummyport",0);
		clientPackets.put("changedailypay",0);
		clientPackets.put("deletelogs",0);
		clientPackets.put("createfolder",0);
		clientPackets.put("put",0);
		clientPackets.put("get",0);
		clientPackets.put("malget",0);
		clientPackets.put("requestfile",0);
		clientPackets.put("requestgame",0);
		clientPackets.put("requestscan",1);
		clientPackets.put("savefile",0);
		clientPackets.put("compilefile",0);
		clientPackets.put("deletemulti",0);
		clientPackets.put("deletefile",0);
		clientPackets.put("setfiledescription",0);
		clientPackets.put("setfileprice",0);
		clientPackets.put("emptypettycash",0);
		clientPackets.put("finalizecancelled",0);
		clientPackets.put("requestattack",1);
		clientPackets.put("requestzombieattack",1);
		clientPackets.put("transfer",0);
		clientPackets.put("deposit",0);
		clientPackets.put("dochallenge",0);
		clientPackets.put("sellfile",0);
		clientPackets.put("sellfilemulti",0);
		clientPackets.put("decompilefile",0);
	}

	//heal limit
	public int HEAL_LIMIT = 9;
	
	//NOOB Safety.
	private int noobLevel=30;

	//SQL CONNECTION INFO.
	private String Connection="127.0.0.1";
	private String DB="hackerforum";
	private String Username="root";
	private String Password="";
	
	private String Connection2="127.0.0.1";
	private String DB2="hackwars_drupal";
	private String Username2="root";
	private String Password2="";
	static final boolean LOCAL_AUTH_FALLBACK=!"false".equalsIgnoreCase(getPropertySafe("hackwars.localAuthFallback","true"));

	EquipmentSheet MyEquipmentSheet=new EquipmentSheet(this);//Keeps track of equipment currently installed and other such things.
    NewFireWall MyNewFireWall = null; //new NewFireWall(); // because I hate static variables, cause they hate me.  Used to generate firewalls.

	int xpTable[]=new int[100];//Table of XP per level.
	static final long ATTACK_RATE=2000;//How frequently should an attack tack place.
	static final long CHANGE_NETWORKS=180000;//How often can the player change networks?

	long lastChangeNetwork=0;//Keep track of the last time the player changed networks.
	long lastAttack=0;//At what time did an attack last take place.
	long healCounter=0;//Used to decide how frequently a port should heal based on Mod.
	static final long PACKET_TIMEOUT=500;//How frequently should we generate a packet?
	long lastSent=0;//Last time that a packet was sent.
	static final long PING_TIMEOUT=20000; //how long should we wait after a ping to determine whether the player has closed the client or not.
	long lastPingTime = 0;
	long logInTime = 0;
	static final long CLIENT_PACKET_TIMEOUT = 600000;
	long lastClientPacketTime = 0;
	static final int CAPTCHA_COUNT = 200;
	
	final long COMPUTER_TIMEOUT=1400000-(int)(700000*Math.random());//How long before we re-write the computer to disk.
	//private static final long COMPUTER_TIMEOUT=60000;//-(int)(1000*Math.random());//How long before we re-write the computer to disk.

	static final long PAY_PERIOD=43200000;//How often should we be paid. (Per Day)
	final long AUTO_SAVE=1200000-(int)(600000*Math.random());//How often should we save the profile?	
	
	int type=0;//Is this an NPC or player?
	float dailyPaySize=1000;//How much do you make a day?
	float dailyPayReduction=1.0f;//Percent value that indicates how much daily pay should be reduced.
	float respawnMoney=0;//How much money does an NPC get when they respawn.
	float maximumPettyCash=0;//For some NPCs we want to limit the cash in their petty, so that they can't be robbed for tons.
	public static final int PLAYER=0;
	public static final int NPC=1;
	public boolean loggedIn = false; //whether the player has logged in, or was accessed.
	public boolean gateway=false;
	public boolean FileIO=true;
	public boolean upgradedAccount = false;
	boolean inactive = false;
	HashMap RecentQuestFinishers=new HashMap();//Players who've recently finished quests.

	long lastSave=0;//When was the last time that the profile was saved.
	boolean systemChange=true;//Has the system changed since we last sent a packet.
	boolean healthChange=true;//Should an update be given regarding the player's current port healths?
	boolean countDown=false;//Is a count down currently taking place?.
	static final long COUNTDOWN_LENGTH=180000;//How long should a countdown take?

	long countDownStart=0;//When did the countdown start?

	//Used to manage errors when the occur during load time.
	boolean LOAD_FAILURE=false;//The XML file Failed To Load.
	boolean LOGOUT=false;//Has a player requested that they be logged out.
	String errorMessage="";//An error message to report back to the player.
	String loadRequester="";//The IP of the individual who requested that this Computer be loaded.

	long lastAccessed=0;//When was the computer last accessed?
	static final long SLEEP_TIME=50;//How often can we process a remote call?
	long lastPaid=0;//When was the last time this player recieved their daily money.
	long overheatStart=-1;//Keep track of when an overheat started.
	public static final long OVER_HEAT_TIME=60000;//How long should an overheat take place for.

	Time MyTime=null;//Central time keeping thread.
	Thread MyThread=null;//The thread associated with this class.

	boolean GUI_READY=false;//This variable is used by the 3D chat to determine whether the GUI is in a state ready to start receiving walking packets.
	boolean Loading=false;//Is the computer currently loading.
	boolean Loaded=false;//Has the computer started loading.
	boolean run=true;//Used to set whether this computer's thread is running.
	boolean LOG_UPDATE=false;//Has the player's DB been updated?
	boolean locked=false;//Has the account been locked down?
	int lockCount=0;
	String unlockKey="";//What key will unlock the account.
	
	//Ports on the computer.
	HashMap Ports=new HashMap();
	
	//Information about Computer.
	String ip="";//IP Address of this computer.
	String userName;//Username associated with this computer.
	String password;//FTP password for this computer.
	int successfulHacks=0;//Number of successful hacks that this player has performed.
	String pageBody="";//Body of personal webpage.
	String pageTitle="";//Title of personal webpage.
	String adRevenueTarget="";//Target that daily pay should be placed in (may be malicious).
	String storeRevenueTarget="";//Target that daily store revenue should be placed in (may be malicious).
	String lastBountyHTTP="";//Keeps track of the last person to take over the daily pay of this computer.
		
	boolean pageChanged=false;//Has the page changed since last output to file system?
	int votes=0;//How many votes does the player currently have.
	int operationCount=0;//How many operations has a player performed since they last logged in?
	
	//Improved Network and Quest Functionality.
	HashMap CurrentQuests=new HashMap();
	ArrayList CompletedQuests=new ArrayList();
	ArrayList InvolvedQuests=new ArrayList();
	String network=Network.ROOT_NETWORK;//Keeps track of the network that this NPC is currently on.
	ArrayList AllowedNetworks=new ArrayList();//The networks a player is allowed access to.
    static final int DEDRICKS_QUEST = 10;
	
	FileSystem MyFileSystem=null;//The file system used for hack wars.
	MakeClue MyMakeClue=null;//The class for generating and checking clues.
	MakeBounty MyMakeBounty=null;//Used for handling bounties.
			
	//Current tasks that have been sent in for this computer to perform.
	final Semaphore available = new Semaphore(1, true);//Make it thread safe.
	ArrayList Tasks=new ArrayList();//The array of tasks.

	//Array of messages since last packet.
	ArrayList Messages=new ArrayList();
	//Array list of damage updates.
	ArrayList Damage=new ArrayList();
	
	//Array list of show choices requests from finalized attacks.
	ArrayList Choices=new ArrayList();
	
	//An instance of the central server used for communicating with client.
	HackerServerBridge MyHackerServer=null;
	int connectionID=-1;//ID of this client connection.
	PacketAssignment PA=new PacketAssignment(0);//The current packet assignment we're building.
	DamageAssignment DA=new DamageAssignment(0);//The current damage assignment we're building.

	//The parent Computer Handler that tasks can be dispatched to.
	NetworkSwitch MyComputerHandler=null;
	ComputerHandler RawComputerHandler=null;
	
	//Handle the watches installed on this computer.
	WatchHandler MyWatchHandler=null;

	//Player's experience in the various skills.
	public static final float CPU_CHART[]=new float[]{50.0f,100.0f,150.0f,200.0f,250.0f,300.0f,75.0f};//Maximum Loads of various CPUs.
	public static final float MEMORY_CHART[]=new float[]{8.0f,16.0f,24.0f,32.0f,8.0f};//Maximum Port Count.
	public static final int WATCH_CHART[]=new int[]{4,6,8,12,5};
	HashMap Stats=new HashMap();//Player statistics are stored in a hash map.
	ArrayList LogMessages=new ArrayList();//Allow players to save messages to their 'DB'.
	ArrayList Globals=new ArrayList();//Allow players to maintain global variables.
	
	int cputype=0;//What type of CPU is installed on this computer.
	int memorytype=0;//What type of Memory is installed on this computer.

	float pettyCash = 0.0f;//Money in petty cash.
	float bankMoney = 0.0f;//Money in bank.
	float currentCPU=0.0f;//The current CPU load.
	float reportCPU=0.0f;//The CPU load reported to the player.
	float baseCPU=0.0f;
	float currentWatchCost=0.0f;//The cost associated with the watches that are currently active.
	int myVotes=0;//How many votes do you have to use on websites you like.
	int voteCount=0;//How many times has your site been voted for.
	//The new commodity banks and pettys.
	String store="";
	public static final int Plutonium=4;
	public static final int YBCO=3;
	public static final int Silicon=2;
	public static final int Germanium=1;
	public static final int DuctTape=0;
	public static String commodityString[]=new String[]{"Duct Tape","Germanium","Silicon","YBCO","Plutonium"};
	public static int requiredRepairLevel[]=new int[]{0,15,45,75,90};
	public float repairXP[]=new float[]{15.0f,30.0f,60.0f,120.0f,240.0f};
	public static float commodityXP[]=new float[]{20.0f,40.0f,100.0f,400.0f,1000.0f};
	float commodityAmount[]=new float[]{0.0f,0.0f,0.0f,0.0f,0.0f};
	float commodityRespawn[]=new float[]{0.0f,0.0f,0.0f,0.0f,0.0f};
	
	//Default ports.
	int defaultBank=0;
	int defaultAttack=0;
	int defaultFTP=0;
	int defaultHTTP=0;
	int defaultShipping=0;
	static final int MAX_PORT=32;
	
	String profile=null;
	
	boolean repaired=false;
	
	//Drop Table info.
	DropTable MyDropTable=null;
	int dropTable=1;
	HackerFile lastDrop = null;
	
	//This hashmap contains all the virtual functions run within the run function.
	HashMap functions=null;
    
    // this hashmap contains the user's preferences
    HashMap preferences=null;
    boolean sendPreferences = false;
	
	//MessageHandler
	MessageHandler messageHandler = new MessageHandler(this);

	private final ComputerSessionService sessionService = new ComputerSessionService();
	private final XmlComputerPersistence xmlComputerPersistence = new XmlComputerPersistence();
	private final LegacyComputerPersistenceSupport persistenceSupport = new LegacyComputerPersistenceSupport(xmlComputerPersistence);
	private final ComputerPacketBuilder standardPacketBuilder = new ComputerPacketBuilder();
	private final ComputerDamagePacketBuilder damagePacketBuilder = new ComputerDamagePacketBuilder();
	private final ComputerRuntimeCoordinator runtimeCoordinator = new ComputerRuntimeCoordinator();
	private CommandDispatcher commandDispatcher = null;
	
	public boolean sentOverHeatedMessage = false;
	
	/**
	This function builds up the initial HashMap of functions.
	*/
	public void buildFunctionHash(){
		functions=new HashMap();
		functions.put("deletelogs",new DeleteLogs(this));
		functions.put("requestftpupdate",new RequestFTPUpdate(this));
		functions.put("requestzombieattack",new RequestZombieAttack(this));
		functions.put("requestattackdefault",new RequestAttackDefault(this));
		functions.put("addshowchoices",new AddShowChoices(this));
		functions.put("bankxp",new BankXP(this));
		functions.put("deletefolder",new DeleteFolder(this));
		functions.put("createfolder",new CreateFolder(this));
		functions.put("code",new SetCode(this));
		functions.put("dochallenge",new DoChallenge(this));
		functions.put("redirectxp",new RedirectXP(this));
		functions.put("repairxp",new RepairXP(this));
		functions.put("watchxp",new WatchXP(this));
		functions.put("httpxp",new HttpXP(this));
		functions.put("scanxp",new ScanXP(this));
		functions.put("setftppassword",new SetFTPPassword(this));
		functions.put("setdefaultport",new SetDefaultPort(this));
		functions.put("requesttrigger",new RequestTrigger(this));
		functions.put("requesttriggernote",new RequestTriggerNote(this));
		functions.put("requestsave",new RequestSave(this));
		functions.put("requesttask",new RequestTask(this));
		functions.put("setdummyport",new SetDummyPort(this));
		functions.put("portonoff",new PortOnOff(this));
		functions.put("saveportnote",new SavePortNote(this));
		functions.put("uninstallport",new UninstallPort(this));
		functions.put("changewatchport",new ChangeWatchPort(this));
		functions.put("launchNetworkAttack",new LaunchNetworkAttack(this));
		commandDispatcher = CommandRegistry.Companion.fromFunctions((Map<String, Function>) functions);
	}
	
	/**
	Return the current daily pay reduction value.
	*/
	public float getDailyPayReduction(){
		return(dailyPayReduction);
	}
	
	public void setDailyPayReduction(float dailyPayReduction){
		this.dailyPayReduction=dailyPayReduction;
	}
			
	/**
	Returns whether or not this account is an NPC.
	*/
	public boolean isNPC(){
		if(type==NPC){
			return(true);
		}
		return(false);
	}
	
	/**
	Add recent quest finishes.
	*/
	public void addRecentQuestFinisher(String ip){
		RecentQuestFinishers.put(ip,"true");
	}	
	
	/**
	Check whether a player has recently finished a quest.
	*/
	public boolean checkRecentQuestFinisher(String ip){
		if(RecentQuestFinishers.get(ip)==null)
			return(false);
		return(true);
	}
	
	/**
	Get the type of cpu currently installed on this computer.
	*/
	public int getCPUType(){
		return(cputype);
	}
	
	/**
	Get the number of votes that a player currently has.
	*/
	public int getVoteCount(){
		return(voteCount);
	}
	
	public void setVoteCount(int voteCount){
		this.voteCount=voteCount;
	}
	
	/**
	Get the noob safety protection.
	*/
	public int getNoobSafety(){
		return(noobLevel);
	}

	ComputerSessionService getSessionService(){
		return sessionService;
	}
	
	/**
	Get the array that is used to indicate the showChoices boxes that should be shown. 
	There may be more than one if multiple attacks are running, hence the array.
	*/
	public ArrayList getShowChoicesArray(){
		return(Choices);
	}
	
	/**
	Set the ad revenue target.
	*/
	public void setAdRevenueTarget(String adRevenueTarget){
		this.adRevenueTarget=adRevenueTarget;
	}
	
	/**
	Return the HashMap of quests a player is currently participating in.
	*/
	public HashMap getCurrentQuests(){
		return(CurrentQuests);
	}
	
	/**
	Get the current target of website revenue.
	*/
	public String getAdRevenueTarget(){
		return(adRevenueTarget);
	}
	
	/**
	Get the main packet being built up since the last time a packet was sent updating the client.
	*/
	public PacketAssignment getPacketAssignment(){
		return(PA);
	}
	
	/**
	Whether the computer is under attack or not
	*/
	public boolean isUnderAttack(){
		Object[] ports = Ports.values().toArray();
		for(int i=0;i<ports.length;i++){
			Port port = (Port)ports[i];
			String accessing = port.getAccessing();
			//System.out.println(port.getNumber()+": "+accessing);
			if(!accessing.equals("")){
				return(true);
			}		
		}
		return(false);
	}
	
	/**
	Get the maximum watches.
	*/
	public int getMaximumWatches(){
		return(WATCH_CHART[memorytype]+(int)MyEquipmentSheet.getWatchBonus());
	}
	
	public int getMaximumWatchesNoBonus(){
		return(WATCH_CHART[memorytype]);
	}
	
	/**
	Update a global variable.
	*/
	public void setGlobal(int index,Object data){
		if(index<20){
			Globals.set(index,data);
		}
	}
	
	/**
	Is this player a member?
	*/
	public boolean getFileIO(){
		return(FileIO);
	}
	
	/**
	Return a global variable.
	*/
	public Variable getGlobal(int index){
		if(index<20){
			return((Variable)Globals.get(index));
		}
		return(null);
	}
	
	/**
	Reset the player's logs.
	*/
	public void resetLogs(){
		LogMessages=new ArrayList();
		LogMessages.add(new String[]{"",""});
		LOG_UPDATE=true;
	}
	
	/**
	Return a string representation of the logs.
	*/
	public String getLogs(){
		if(LogMessages==null)
			return("");
		String returnMe="";
		for(int i=0;i<LogMessages.size();i++){
			String temp=((String[])LogMessages.get(i))[0];
			if(!temp.equals("null")){
				returnMe+=temp+"\n";
			}
		}
		return(returnMe);
	}
	
	/**
	Get the current store IP for the network the player is on.
	*/
	public String getStoreIP(){
		return(store);
	}
	
	/**
	Edit the logs associated with this account.
	*/
	public void editLogs(String data,String replace){
		replace=replace.replaceAll("\\\\", "\\\\\\\\");
		replace=replace.replaceAll("\\$", "\\\\\\$");
		data=HackerLinker.regexEscape(data);
	
		for(int i=0;i<LogMessages.size();i++){
			String content[]=(String[])LogMessages.get(i);
			if(content!=null){
				content[0]=content[0].replaceAll(data,replace);
				LogMessages.set(i,content);
			}
		}
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Allow player's to save messages to their 'DB'.
	*/
	public void logMessage(String message,String ip,Long timestamp){
		Calendar c = Calendar.getInstance();
		if(timestamp!=-1){
			c.setTimeInMillis(timestamp);
		}
		int month=c.get(c.MONTH);
		String monthString="Dec";
		if(month==c.JANUARY)
			monthString="Jan";
		if(month==c.FEBRUARY)
			monthString="Feb";
		if(month==c.MARCH)
			monthString="Mar";
		if(month==c.APRIL)
			monthString="Apr";
		if(month==c.MAY)
			monthString="May";
		if(month==c.JUNE)
			monthString="Jun";
		if(month==c.JULY)
			monthString="Jul";
		if(month==c.AUGUST)
			monthString="Aug";
		if(month==c.SEPTEMBER)
			monthString="Sep";
		if(month==c.OCTOBER)
			monthString="Oct";
		if(month==c.NOVEMBER)
			monthString="Nov";
		if(month==c.DECEMBER)
			monthString="Dec";
			
		int dayOfMonth=c.get(c.DAY_OF_MONTH);
		int year=c.get(Calendar.YEAR);
		
		int hour=c.get(Calendar.HOUR);
		int minute=c.get(Calendar.MINUTE);
		int seconds=c.get(Calendar.SECOND);
		int ampm=c.get(Calendar.AM_PM);
		String pm="PM";
		String second;
		String minutes;
		if(seconds<10)
			second="0"+seconds;
		else
			second=""+seconds;
		if(minute<10)
			minutes="0"+minute;
		else
			minutes=""+minute;
		if(ampm==0)
			pm="AM";
			
		if(hour==0)
			hour=12;
			
		String stamp=dayOfMonth+"-"+monthString+"-"+year+" ("+hour+":"+minutes+":"+second+" "+pm+")";
		LogMessages.add(new String[]{stamp+" "+message,ip});
		if(LogMessages.size()>50)
			LogMessages.remove(0);
			
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Deletes all the logs corresponding to a specific IP.
	*/
	public void deleteLogs(String ip){
		Iterator LogIterator=LogMessages.iterator();
		while(LogIterator.hasNext()){
			String[] S=(String[])LogIterator.next();
			if(S[1].equals(ip))
				LogIterator.remove();
		}
		
			try{
				Object[] params = new Object[]{ip,this.ip};
				sessionService.executeRemote("http://www.hackwars.net/xmlrpc/facebook.php","deleteLogs",params);
			}catch(Exception e){
				e.printStackTrace();
			}
		LOG_UPDATE=true;
		sendPacket();
	}
	
	/**
	Returns the IP of the last player ot take over this computer's HTTP as
	part of a bounty.
	*/
	public String getLastBountyHTTPIP(){
		return(lastBountyHTTP);
	}
	
	/**
	Return the equipment sheet for use by other aspects of the computer.
	*/
	public EquipmentSheet getEquipmentSheet(){
		return(MyEquipmentSheet);
	}
    
    public NewFireWall getNewFireWall() {
        return(MyNewFireWall);
    }
	
	/**
	Get whether or not the overheat timeout has finished.
	*/
	public long getOverheatStart(){
		return(overheatStart);
	}
	
	/**
	Set the player's hash.
	*/
	private String clientHash="";
	public void setClientHash(String clientHash){
		this.clientHash=clientHash;
	}
	
	/**
	Set the player's public key.
	*/
	public byte[] publicKey=null;
	public void setPublicKey(byte[] publicKey){
		this.publicKey=publicKey;
	}
	
	/**
	Respawn the NPC character.
	*/
	public void respawn(int HackType){
		if(type==NPC){
			if(MyDropTable==null)
				MyDropTable=new DropTable(dropTable,this);
		
			if(HackType==Port.BANKING){
				Random rgen = new Random();
				float respawnAmount = respawnMoney + rgen.nextFloat()*(2*(respawnMoney*0.25f)) - (respawnMoney*0.25f);
				pettyCash = respawnAmount;
				//pettyCash=respawnMoney;
			}else if(HackType==Port.FTP){
				HackerFile HF=MyDropTable.generateDrop();
				Object Parameter[]=new Object[]{"Public/",HF};
				HF.setLocation("Public/");
				MyComputerHandler.addData(new ApplicationData("savefile",Parameter,0,ip),ip);
			}
		}
	}
	
	/**
	Return the last dropped file
	*/
	public HackerFile getDrop(){
		return(lastDrop);
	}
	
	/**
	set the last dropped file
	*/
	public void setDrop(HackerFile file){
		lastDrop = file;
	}
	
	/**
	returns the drop table associated with this Computer.
	*/
	public DropTable getDropTable(){
	if(MyDropTable==null)
				MyDropTable=new DropTable(dropTable,this);
		return(MyDropTable);
	}
	
	/**
	Return the computer handler associated with this computer.
	*/
	public NetworkSwitch getComputerHandler(){
		return(MyComputerHandler);
	}
	
	/**
	Get the type of computer (NPC, or Player)
	*/
	public int getType(){
		return(type);
	}
	
	/**
	Start count down.
	*/
	public void startCountDown(){
		systemChange=true;
		countDown=true;
		countDownStart=MyTime.getCurrentTime();
	}
	
	/**
	Get the current time.
	*/
	public long getCurrentTime(){
		return(MyTime.getCurrentTime());
	}
	
	/**
	Get the server id associated with this computer.
	*/
	public String getServerID(){
		return(MyHackerServer.getServerID());
	}
	
	/**
	Get the watch level of the player.
	*/
	public float getWatchLevel(){
		return(getLevel((float)((Float)Stats.get("Watch"))));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getAttackLevel(){
		return(getLevel((float)((Float)Stats.get("Attack"))));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getBankLevel(){
		return(getLevel((float)((Float)Stats.get("Bank"))));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getScanningLevel(){
		return(getLevel((float)((Float)Stats.get("Scanning"))));
	}
	
	/**
	Get the attack level of the player.
	*/
	public float getFireWallLevel(){
		return(getLevel((float)((Float)Stats.get("FireWall"))));
	}
		
	/**
	Get the HTTP level of the player.
	*/
	public float getHTTPLevel(){
		return(getLevel((float)((Float)Stats.get("Webdesign"))));
	}
	
	/**
	Get the Redirecting level of the player.
	*/
	public float getRedirectingLevel(){
		return(getLevel((float)((Float)Stats.get("Redirecting"))));
	}
	
	/**
	Get the repair level of the player.
	*/
	public float getRepairLevel(){
		return(getLevel((float)((Float)Stats.get("Repair"))));
	}
	
	/**
	Return the HashMap that is used to store a player's XP in various skills.
	*/
	public HashMap getStats(){
		return(Stats);
	}
	
	/**
	Set the store revenue target.
	*/
	public void setStoreRevenueTarget(String storeRevenueTarget){	
		this.storeRevenueTarget=storeRevenueTarget;
	}
	
	/**
	Set the IP address of the individual requesting that this profile be loaded.
	*/
	public void setLoadRequester(String loadRequester){
		this.loadRequester=loadRequester;
	}
	
	/**
	Get the watch handler attached to this computer.
	*/
	public WatchHandler getWatchHandler(){
		return(MyWatchHandler);
	}
	
	/**
	Get the password associated with this account's public features.
	*/
	public String getPassword(){
		return(password);
	}
	
	public void setPassword(String password){
		this.password=password;
	}
			
	/**
	Destroy all the watches installed on the given port number.
	*/
	public void destroyWatches(int port){
		MyWatchHandler.destroyWatches(port);
	}
	
	/**
	Increment the number of successful hacks that a player has performed.
	*/
	public void incrementSuccessfulHacks(){
		successfulHacks++;
	}
	
	/**
	Get the HashMap of ports installed on this computer.
	*/
	public HashMap getPorts(){
		return(Ports);
	}
	
	/**
	Get the title of the player's webpage.
	*/
	public String getTitle(){
		return(pageTitle);
	}
	
	/**
	Get the body of the player's webpage.
	*/
	public String getBody(){
		return(pageBody);
	}
	
	/**
	Tell the Computer to send a packet.
	*/
	public void sendPacket(){
		systemChange=true;
	}
	
	/**
	Send a damage packet.
	*/
	public void sendDamagePacket(){
		healthChange=true;
	}
	
	/**
	Get/Set whether or not a piece of hardware has been repaired during this session.
	*/
	public void setRepaired(boolean repaired){
		this.repaired=repaired;
	}
	
	public boolean getRepaired(){
		return(repaired);
	}

	/**
	Get the default bank associated with this computer.
	*/
	public int getDefaultBank(){
		return getPortOn(defaultBank, Port.BANKING);
	}
	
	/**
	Return a port that is On if the default happens to be off.
	*/
	public int getPortOn(int defaultPort, int portType) {
	
		//First use the default port value
		Port checkPort = (Port)Ports.get(defaultPort);
				
		if(checkPort != null) {
			if(checkPort.getType() == portType && checkPort.getOn())
				return defaultPort;
		}
				
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==portType&&TempPort.getOn()&&!TempPort.getDummy()) {
				return TempPort.getNumber();
			}
			ii++;
		}
		
		return 0;
	}
	
	public void setDefaultBank(int defaultBank){
		this.defaultBank=defaultBank;
	}
	
	/**
	Get the default attack port associated with this computer.
	*/
	public int getDefaultAttack(){
		return getPortOn(defaultAttack, Port.ATTACK);
	}
	
	public void setDefaultAttack(int defaultAttack){
		this.defaultAttack=defaultAttack;
	}
	
	/**
	Return the default port used for shipping commodities.
	*/
	public int getDefaultShipping(){
		return getPortOn(defaultShipping, Port.SHIPPING);
	}
	
	public void setDefaultShipping(int defaultShipping){
		this.defaultShipping=defaultShipping;
	}
	
	/**
	Get the default HTTP port associated with this computer.
	*/
	public int getDefaultHTTP(){
		return getPortOn(defaultHTTP, Port.HTTP);
	}
	
	public void setDefaultHTTP(int defaultHTTP){
		this.defaultHTTP=defaultHTTP;
	}
	
	
	/**
	Get the default FTP port associated with this computer.
	*/
	public int getDefaultFTP(){
		return getPortOn(defaultFTP, Port.FTP);
	}
	
	public void setDefaultFTP(int defaultFTP){
		this.defaultFTP=defaultFTP;
	}
	
	/**
	Add a message to be dispatched to the client.
	*/
	public void addMessage(String Message){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(new Object[]{Message, messageHandler.GAME_MESSAGE},null);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,null);
			systemChange=true;
		}
	}
	
	public void addMessage(String Message,Object[] parameters){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(new Object[]{Message, messageHandler.GAME_MESSAGE},parameters);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message,Object[] parameters){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,parameters);
			systemChange=true;
		}
	}	
	
	public void addMessage(Object[] Message,Object[] parameters,Object[] portInfo){
		if(connectionID!=-1){
			//Messages.add(Message);
			messageHandler.addMessage(Message,parameters,portInfo);
			systemChange=true;
		}
	}
	
	public ArrayList getMessages(){
		return(Messages);
	}

	/**
	Returns the amount of money in the Player's petty cash.
	This is the money that can be stollen.
	*/
	public float getPettyCash(){
		return(pettyCash);
	}
	
	
	/**
	Gets the current amount of a given commodity in this computers
	commodity stores.
	*/
	public float getCommodity(int commodityType){
		return(commodityAmount[commodityType]);
	}
	
	/**
	Respawn the commodity.
	*/
	public void respawnCommodity(int commodityType){
		if(type==NPC&&commodityAmount[commodityType]<=0.0)
			commodityAmount[commodityType]=commodityRespawn[commodityType];
	}
	
	/**
	Sets the amount of the given commodity.
	*/
	public void setCommodityAmount(int commodityType,float amount){
		this.commodityAmount[commodityType]=amount;
		
		if(type==NPC&&amount<=0){
			this.commodityAmount[commodityType]=commodityRespawn[commodityType];
		}
		
		sendPacket();
	}
	
	/**
	Set the amount of money in the player's petty cash.
	*/
	public void setPettyCash(float pettyCash){
		this.pettyCash=pettyCash;
		if(maximumPettyCash>0&&this.pettyCash>maximumPettyCash)
			this.pettyCash=maximumPettyCash;
		if(pettyCash<0)
			pettyCash=0;
	}
	
	/**
	Returns the amount of money in the Player's bank.
	This is the money that can be stollen.
	*/
	public float getBank(){
		return(bankMoney);
	}
	
	/**
	Set the amount of money in the player's bank.
	*/
	public void setBank(float bankMoney){
		this.bankMoney=bankMoney;
	}
	
	/**
	Return the maximum CPU load based on the current CPU installed.
	*/
	public float getMaximumCPULoad(){
		return(CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus());
	}
	
	public float getMaximumCPUNoBonus(){
		return(CPU_CHART[cputype]);
	}
	
	/**
	Returns whether or not this is a gateway NPC.
	*/
	public boolean isGateway(){
		return(gateway);
	}
	
	/**
	Get the network that this NPC is currently on.
	*/
	public String getNetwork(){
		return(network);
	}
	
	/**
	Return the amount of money in the player's bank.
	This money cannot be hacked.
	*/
	public float getBankMoney(){
		return(bankMoney);
	}
	
	/**
	Get the clue level of this computer.
	*/
	public int getClueLevel(){
		return(0);
	//	return(clueLevel);
	}
	
	/**
	Return the current CPU load of this computer.
	*/
	public float getCPULoad(){
		return(reportCPU);
	}
	
	/**
	Return the base CPU load before overheating is taken into account.
	*/
	public float getBaseCPULoad(){
		return(baseCPU);
	}
	
	/**
	Return the file system object.
	*/
	public FileSystem getFileSystem(){
		return(MyFileSystem);
	}
	
	/**
	Get the base amount of damage this player currently deals.
        Type = "Attack" or "Redirecting"
	*/
	public float getDamage(String type){
		float damage=2.0f;
		float attackXP=(Float)Stats.get(type);
		int i=0;
		try {
			while( ((int)attackXP > xpTable[i]) && i < 99) {
				i++;
			}
		} catch(ArrayIndexOutOfBoundsException e) {}
		//damage+=(float)((i+1)/5);
        damage += (float) ((i+1) * 0.2f);
		return(damage);
	}
	
	/**
	Get the current level of a stat based on the current XP.
	*/
	public int getLevel(float xp){
		int i=0;
		try{
			while(((int)xp>xpTable[i])&&i<99){
				i++;
			}
		}catch(ArrayIndexOutOfBoundsException e){}
		return(i+1);
	}
	
	/**
	Set whether this computer's thread should currently be running.
	*/
	public void setRun(boolean run){
		Network.getInstance(MyComputerHandler).removeFromNetwork(network,ip);//Remove the player from their current network.
		this.run=run;
		Thread thread = MyThread;
		if(thread!=null){
			thread.interrupt();
		}
		MyThread=null;
	}
	
	/**
	Get the class for checking/generating clues.
	*/
	public MakeClue getMakeClue(){
		return(MyMakeClue);
	}
	
	public ArrayList getDamage(){
		return(Damage);
	}
	
	public void returnPlayer(String profile){
		this.profile=profile;
	}
	
	/**
	Constructor.
	*/
	public Computer(String ip,ComputerHandler MyComputerHandler,Time MyTime,int connectionID,HackerServerBridge MyHackerServer){
		for(int i=0;i<20;i++)
			Globals.add(null);
	
		MyFileSystem=new FileSystem(this);//The virtual file sytem.
	//	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
		MyMakeBounty=new MakeBounty(MyFileSystem);//Used for generating bounties.
	
		//Create the experience table.
		int xp=83;
		int xpDiff=83;
		for(int i=0;i<100;i++){
			xpTable[i]=xp;
			xpDiff+=xpDiff/9.525;
			xp+=xpDiff;
		}
		
		this.MyComputerHandler=new NetworkSwitch(this,MyComputerHandler);
		this.RawComputerHandler=MyComputerHandler;
        this.MyNewFireWall = new NewFireWall(this.MyComputerHandler);
		this.ip=ip;
		this.MyTime=MyTime;
		this.connectionID=connectionID;
		this.MyHackerServer=MyHackerServer;
		
		MyWatchHandler=new WatchHandler(MyComputerHandler,this);
		
		while(lastAccessed==0)
			this.lastAccessed=MyTime.getCurrentTime();
		MyThread = new Thread(this,"Computer - "+ip);
		MyThread.start();
	}
	
	/**
	Constructor.
	*/
	public Computer(String userName,String ip,ComputerHandler MyComputerHandler,Time MyTime,int connectionID,HackerServerBridge MyHackerServer,boolean playerLogin){
		for(int i=0;i<20;i++)
			Globals.add(null);
	
		MyFileSystem=new FileSystem(this);//The virtual file sytem.
	//	MyMakeClue=new MakeClue(MyFileSystem,this,clueLevel);//The clue generator/checker.
		MyMakeBounty=new MakeBounty(MyFileSystem);//Used for generating bounties.

		this.ip=ip;
	
		//Create the experience table.
		int xp=83;
		int xpDiff=83;
		for(int i=0;i<100;i++){
			xpTable[i]=xp;
			xpDiff+=xpDiff/9.525;
			xp+=xpDiff;
		}
		
		this.MyComputerHandler=new NetworkSwitch(this,MyComputerHandler);
		this.RawComputerHandler=MyComputerHandler;
		
		this.userName=userName;
		this.MyTime=MyTime;
		this.connectionID=connectionID;
		this.MyHackerServer=MyHackerServer;
		
		MyWatchHandler=new WatchHandler(MyComputerHandler,this);
		
		while(lastAccessed==0)
			this.lastAccessed=MyTime.getCurrentTime();
		MyThread = new Thread(this,"Computer - "+ip);
		MyThread.start();
	}

	
	/**
	Set the connection ID associated with this computer.
	If a player is already logged in and reconnects this is necessary.
	*/
	boolean RESEND_CAPTCHA=false;
	public void setConnectionID(int connectionID,String loginPassword){
		try{
			available.acquire();
			RESEND_CAPTCHA=true;
			this.lastAccessed=MyTime.getCurrentTime();
			loadRequester="";
			Tasks.add(0,new setConnectionIDTask(this,connectionID,crypt(loginPassword.getBytes(),clientHash)));
			available.release();
			
					if(REMOTE_XMLRPC_ENABLED){
						try{
							sessionService.requestFunctionPacks(ip);
						}catch(Exception e){
							e.printStackTrace();
						}
				}
				FileIO=true;
		}catch(Exception e){
			available.release();
			e.printStackTrace();	
		}
	}

	public void setConnectionID(int connectionID){
		try{
			available.acquire();
			RESEND_CAPTCHA=true;
			this.lastAccessed=MyTime.getCurrentTime();
			loadRequester="";
			Tasks.add(0,new setConnectionIDTask(this,connectionID,null));
			available.release();
			FileIO=true;
		}catch(Exception e){
			available.release();
			e.printStackTrace();
		}
	}
	
	/**
	Used to encrypt and decrypt XOR encrypted data.
	*/
	static public String crypt (byte [] data,String key)
	{
		for (int ii = 0; ii < data.length;ii++) {
			data [ii] ^= (int)(key.getBytes())[ii%key.length()]; 
		}
		return(new String(data));
	}
	
	/**
	Set a connection ID in a threaded environment.
	*/
	private class setConnectionIDTask implements Task{
		private int connectionID=-1;
		private String loginPassword="";
		private Computer MyComputer=null;
		
		public setConnectionIDTask(Computer MyComputer,int connectionID,String loginPassword){
			this.connectionID=connectionID;
			this.loginPassword=loginPassword;
			this.MyComputer=MyComputer;
		}
		
		public void execute(){
			if(loginPassword!=null){
				MyComputer.loginPassword=loginPassword;
			}
			if(checkLogin()){
				MyComputer.connectionID=connectionID;//The ID used to relay data back to the client-side.
				
				MyHackerServer.removeRandomKey(ip);
				Object O[]=MyHackerServer.getRandomKey(ip,clientHash,publicKey);
				LoginSuccessAssignment MyLoginSuccessAssignment=new LoginSuccessAssignment(0,ip,(String)O[0],isNPC());
				MyLoginSuccessAssignment.setPublicKey((byte[])O[1]);
				O=new Object[]{MyLoginSuccessAssignment,new Integer(connectionID)};
				MyHackerServer.addData(O);
				//Make sure we resend the network information.
				if(network!=null){
					Network.getInstance(MyComputerHandler).getNetworkInformation(network);
					PA.setPacketNetwork(Network.getInstance(MyComputerHandler).getNetworkInformation(network));
				}
				
				//Remove and re-add a player to the 3D chat.
				GUI_READY=false;//Don't allow packets to send since we can't trust the GUI is ready, the first packet we receive GUI side will set this to true.
				//WorldSingleton.getInstance().invalidatePlayer("game",ip);//Add the player into the 3D chat.
				//WorldSingleton.getInstance().addPlayer("game",ip,userName,npc);//Add the player into the 3D chat.
				
				//Force a packet update.
				systemChange=true;
				healthChange=true;
				LOG_UPDATE=true;
			}else{
				Object O[]=new Object[]{new LoginFailedAssignment(0),new Integer(connectionID)};
				MyHackerServer.addData(O);
			}
		}
	}
		
	/**
	getIP()
	returns the ip of the computer.
	**/
	public String getIP(){
		return(this.ip);
	}
	
	/**
	addData()
	Adds a function to be processed using a semaphore into the
	computer's processing stack.
	*/
	public void addData(Object MyApplicationData){
		try{
			available.acquire();
			if(Tasks.size()<50){
				operationCount+=1;//Keep track of how many operations have been peformed while this player is logged in.
				ApplicationData MAD=(ApplicationData)MyApplicationData;
				//We must make sure that the transactional data gets moved to the front of the list.
				boolean applicationData=false;
				if(MyApplicationData instanceof ApplicationData)
					applicationData=true;
				
				//if(!applicationData||!locked||!((ApplicationData)MyApplicationData).getSourceIP().equals(ip)||((ApplicationData)MyApplicationData).getSource()!=ApplicationData.OUTSIDE||connectionID==-1){
					boolean add = true;
                    if(locked){
						Object packet = clientPackets.get(MAD.getFunction());
						if(packet!=null){
							int count = (Integer)packet;
							if(count > 0){
								add = false;
							}
						}
					}
					if(add){
						if(MAD.getFunction().equals("bank")||MAD.getFunction().equals("pettycash"))
							Tasks.add(0,MyApplicationData);
						else
							Tasks.add(MyApplicationData);
					}
						
				//}
			}
			available.release();
		}catch(Exception e){
			e.printStackTrace();	
            available.release();
		}
	}
	
	/**
	Is the computer currently loading.
	*/
	public boolean getLoading(){
		return(Loading);
	}
	
	/**
	Is the computer completely loaded.
	*/
	public boolean getLoaded(){
		return(Loaded);
	}
	
	/**
	Set the password associated with a player.
	*/
	String loginPassword="";
	boolean playFabAuthenticated=false;
	public void setPlayFabAuthenticated(String userName){
		playFabAuthenticated=true;
		if(userName!=null&&userName.trim().length()>0){
			this.userName=userName;
		}
	}
	public void setLoginPassword(String loginPassword){
		this.loginPassword=crypt(loginPassword.getBytes(),clientHash);
	}
	
	/**
	private void checkLogin(String userName,String loginPassword){
		this.userName=userName;
		this.loginPassword=loginPassword;
		checkLogin();
	}*/
	
	/**
	Check to make sure that username and password are correct.
	*/
	public boolean checkLogin(){
		LoginRequest loginRequest = new LoginRequest(
			ip,
			userName,
			loginPassword,
			playFabAuthenticated,
			ServerRuntimeState.isTesting()
		);
		game.computer.session.LoginResult result = sessionService.authenticate(loginRequest);
		sendPreferences = result.getSendPreferences();
		return result.getAccepted();
	}
	
	/**
	Get the total level of a player.
	*/
	public int getTotalLevel(){
		int totalLevel=0;
		totalLevel+=getLevel((float)(Float)Stats.get("Attack"));
		totalLevel+=getLevel((float)(Float)Stats.get("Bank"));
		totalLevel+=getLevel((float)(Float)Stats.get("Watch"));
		totalLevel+=getLevel((float)(Float)Stats.get("Scanning"));
		totalLevel+=getLevel((float)(Float)Stats.get("FireWall"));
		totalLevel+=getLevel((float)(Float)Stats.get("Webdesign"));
		totalLevel+=getLevel((float)(Float)Stats.get("Redirecting"));
		totalLevel+=getLevel((float)(Float)Stats.get("Repair"));


		return(totalLevel);
	}
	
	/**
	Performs a challenge XML-RPC call.
	*/
	public void doChallengeRPC(String challengeID,String source){
	
		addMessage(MessageHandler.CHALLENGE_START,new Object[]{challengeID});
		addMessage("-----------------------");
		
		//Fetch the HackerFile associated with this ID.
		HackerFile ChallengeFile=null;
		ArrayList ChallengeFiles=MyFileSystem.getFilesOfType(HackerFile.CHALLENGE);
		HashMap Content=null;
		if(ChallengeFiles!=null)
		for(int i=0;i<ChallengeFiles.size();i++){
			HackerFile TempFile=(HackerFile)ChallengeFiles.get(i);
			Content=TempFile.getContent();
			String identifier=(String)Content.get("identifier");
			if(identifier!=null)
			if(challengeID.equals(identifier)){
				ChallengeFile=TempFile;
				break;
			}
		}
		
		if(ChallengeFile==null){//Did we find this file.
			addMessage(MessageHandler.CHALLENGE_NO_FILE);
			return;
		}
		
		String input=(String)Content.get("input");
		String output=(String)Content.get("output");
		String inputMultiple[]=input.split("&");
		String outputMultiple[]=output.split("&");
		String inputtype=(String)Content.get("inputtype");
		String outputtype=(String)Content.get("outputtype");
		int QuestID=new Integer((String)Content.get("questid"));
		String TaskName=(String)Content.get("task");
		
		boolean success=true;
		HashMap result=null;
		for(int ii=0;ii<inputMultiple.length;ii++){
			addMessage(MessageHandler.CHALLENGE_RUNNING_ATTEMPT,new Object[]{(ii+1)});
				
			try{	
				input=inputMultiple[ii];
				output=outputMultiple[ii];
						
				String[] sendStringInput=new String[0];
				Double[] sendDoubleInput=new Double[0];
				Integer[] sendIntegerInput=new Integer[0];
				String[] sendStringOutput=new String[0];
				Double[] sendDoubleOutput=new Double[0];
				Integer[] sendIntegerOutput=new Integer[0];
					
				
				if(inputtype.equals("String")){
					sendStringInput=input.split(",");
				}
				if(outputtype.equals("String")){
					sendStringOutput=output.split(",");
				}
				
					
				if(inputtype.equals("float")){
					String[] ss = input.split(",");
					sendDoubleInput = new Double[ss.length];
					for(int i=0;i<ss.length;i++){
						sendDoubleInput[i] = Double.parseDouble(ss[i]);
					}
				}
				if(outputtype.equals("float")){
					String[] ss = output.split(",");
					sendDoubleOutput = new Double[ss.length];
					for(int i=0;i<ss.length;i++){
						sendDoubleOutput[i] = Double.parseDouble(ss[i]);
					}
				}
					
				if(inputtype.equals("int")){
					String[] ss = input.split(",");
					sendIntegerInput = new Integer[ss.length];
					for(int i=0;i<ss.length;i++){
						sendIntegerInput[i] = Integer.parseInt(ss[i]);
					}
				}
				if(outputtype.equals("int")){
					String[] ss = output.split(",");
					sendIntegerOutput = new Integer[ss.length];
					for(int i=0;i<ss.length;i++){
						sendIntegerOutput[i] = Integer.parseInt(ss[i]);
					}
					
				}

				result=ChallengeRunner.getInstance().runToyProblem(source,new Integer(3000),sendDoubleInput,sendStringInput,sendIntegerInput,sendDoubleOutput,sendStringOutput,sendIntegerOutput);
									
				success = (boolean)(Boolean)result.get("success");
				
				Object[] stringOut = (Object[])result.get("outstring");
				Object[] doubleOut = (Object[])result.get("outdouble");
				Object[] intOut = (Object[])result.get("outint");
						
				for(int i=0;i<stringOut.length;i++){
					addMessage("String Outputted: "+stringOut[i]);
				}

				for(int i=0;i<doubleOut.length;i++){
					addMessage("Float Outputted: "+doubleOut[i]);
				}
				
				for(int i=0;i<intOut.length;i++){
					addMessage("Int Outputted: "+intOut[i]);
				}
				
				if(!success){
					addMessage((String)result.get("error"));
					break;
				}

			}catch(Exception e){
				e.printStackTrace();
			}
						
		}
		addMessage("-----------------------");
		
		//Reward.
		if(success){
			//We'd set a quest parameter to true here.
			String TaskLabel="";
			
			if(!checkQuest(QuestID)){//Make sure the quest isn't already complete.
				HashMap CurrentQuest=null;
				String label="";
				if(CurrentQuests.get(QuestID)!=null){
					CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
					label=(String)((Object[])CurrentQuests.get(QuestID))[1];
				}
												
				if(CurrentQuest==null){
					CurrentQuest=new HashMap();
					CurrentQuest.put(TaskName,new Object[]{new Boolean(true),TaskLabel});
					CurrentQuests.put(QuestID,new Object[]{CurrentQuest,label});
				}else{
					CurrentQuest.put(TaskName,new Object[]{new Boolean(true),TaskLabel});
				}
			}
			
			addMessage(MessageHandler.CHALLENGE_COMPLETED);
		}else{
			addMessage(MessageHandler.CHALLENGE_FAILED);
		}
	}
	
	/**
	Tells the computer to begin loading player data in its thread.
	*/
	public void loadSave(){
		try{
			if(!Loading){
				available.acquire();
				Loading=true;
				Tasks.add(0,new loadSaveTask(this));
				available.release();
			}
		}catch(Exception e){
			available.release();
			e.printStackTrace();	
		}
	}
	
	
	/**
	Tells the computer to begin writing its data to the DB/XML files.
	*/
	public void writeSave(){
		try{
			Loaded=false;
			//Write stuff to the database.
		}catch(Exception e){
			e.printStackTrace();	
		}
	}
	
	/**
	Checks whether the banking port is on before an action can be performed.
	*/
	public boolean checkBank(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		

		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.BANKING&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether a fire wall is installed on any of their ports.
	*/
	public boolean checkFirewall(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.

		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getFireWall()!=null&&!((String)TempPort.getFireWall().getType().get("name")).equals("None")){
				success=true;
				break;
			}
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether a mining port is currently on and ready to accept a transaction.
	*/
	public boolean checkShipping(){
	
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.SHIPPING&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether the http port is on before an action can be performed.
	*/
	public boolean checkHTTP(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.HTTP&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Checks whether or not any of the ports on this computer are attacking.
	*/
	public boolean getAttacking(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getAttacking())
				return(true);
			ii++;
		}
		return(false);
	}
	
	/**
	Checks whether the http port is on before an action can be performed.
	*/
	public boolean checkFTP(){
		Iterator PortIterator=Ports.entrySet().iterator();
		int ii=0;
		boolean success=false;//MAKE SURE THAT A BANKING PORT IS INSTALLED.
		while(PortIterator.hasNext()){
			Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
			if(TempPort.getType()==TempPort.FTP&&TempPort.getOn()&&!TempPort.getDummy())
				success=true;
			ii++;
		}
		return(success);
	}
	
	/**
	Check whether or not a quest is finished.
	*/
	public boolean checkQuest(int ID){
		for(int i=0;i<CompletedQuests.size();i++){
			int check=(Integer)((Object[])CompletedQuests.get(i))[0];
			if(check==ID)
				return(true);
		}
		return(false);
	}
	
	
	private int getWindowHandle(Port P){
		int windowHandle = 0;
		if(P!=null){
			Program program = P.getProgram();
			if(program instanceof AttackProgram){
				windowHandle = ((AttackProgram)program).getWindowHandle();
			}
			else if(program instanceof ShippingProgram){
				windowHandle = ((ShippingProgram)program).getWindowHandle();
			}
		}
		return(windowHandle);
	}
    
    
	/**
		Check whether the file needs to be renamed.
	
	*/
	
	public HackerFile checkRename(HackerFile HF,String path){
		HackerFile HFCheck = MyFileSystem.getFile(path,HF.getName());
		if(HFCheck!=null){
			if(HFCheck.isStacking()){
				if(HF.checkSumFailed(HFCheck)||(HF.getType()==HF.BOUNTY&&ip.equals(store))){
					String nameCheck = HF.getName();
					int i=0;
					String name=nameCheck+i;
					HackerFile TF=null;
					HF.setName(name);
				  //  System.out.println("Changing name to "+name);
					if(HF.getType()!=HF.BOUNTY){//Check for identical files, bounties are a special case.
						while((TF=MyFileSystem.getFile(path,name))!=null&&HF.checkSumFailed(TF)){
							i++;
					//        System.out.println("Changing name to "+(nameCheck+i));
							name=nameCheck+i;
							HF.setName(name);
						}
					}else{
						while((TF=MyFileSystem.getFile(path,name))!=null){
							i++;
							name=nameCheck+i;
							HF.setName(name);
						}
					}
					
					HF.setName(name);
					if(TF==null||HF.checkSumFailed(TF))
						HFCheck=null;
					else
						HFCheck=TF;
				}
			}
		}
		return(HF);
	}
	
    /**
        Save the file to the file system after checking the checksum, etc.,
        */
    public void saveFile(HackerFile HF,HackerFile HFCheck,String path){
        String nameCheck=HF.getName();
        HF = checkRename(HF,path);
		
        int quantity=HF.getQuantity();
        if(quantity==0)
            quantity=1;
        
        float price=0;
        if(HFCheck!=null){//Does the file already exist on disk?
            if(HFCheck.getQuantity()==-1){
                quantity=-1;
            }else if(HFCheck.isStacking()&&HF.getName().equals(HFCheck.getName())){
                quantity=HFCheck.getQuantity()+HF.getQuantity();
            }
        }
        HF.setQuantity(quantity);
    
        if(!MyFileSystem.addFile(HF,true)){//Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL);
        }
        
        if(path.equals("Public/")||path.equals("Store/")){
            PA.setRequestSecondary(true,8);
            PA.setRequestPrimary(true,8);
        }else{
            PA.setRequestPrimary(true,1);
        }
        systemChange=true;
    }
	
	/**
		TTJ's method to bypass file quantity calculation and instead set it the the value passed in parameter
		(fix for takeFile bug)
		basically a clone of the saveFile() function
		*/
	private void saveFileTemp(HackerFile HF, HackerFile HFCheck, String path, int newQuantity) {
		String nameCheck=HF.getName();
        HF = checkRename(HF,path);
		
        int quantity=HF.getQuantity();
        if(quantity==0)
            quantity=1;
        
        float price=0;
        if(HFCheck!=null){//Does the file already exist on disk?
            if(HFCheck.getQuantity()==-1){
                quantity=-1;
            }else if(HFCheck.isStacking()&&HF.getName().equals(HFCheck.getName())){
                quantity=HFCheck.getQuantity()+HF.getQuantity();
            }
        }
        HF.setQuantity(newQuantity); // sorry, but i'm ignoring all that crap about quantity
    
        if(!MyFileSystem.addFile(HF,true)){//Check whether the file system can accept a new file.
            addMessage(MessageHandler.HD_FULL);
        }
        
        if(path.equals("Public/")||path.equals("Store/")){
            PA.setRequestSecondary(true,8);
            PA.setRequestPrimary(true,8);
        }else{
            PA.setRequestPrimary(true,1);
        }
        systemChange=true;
	}

	/**
    Fetch execution tasks from the stack.
    */
	private int iterationCount=0;
	private boolean cpuLoadCalculated=false;
    public synchronized void run(){
        while(run){
			iterationCount++;
            long startTime=MyTime.getCurrentTime();
			
			try{
				//LOCK OUR LIST AND POP ONE ENTRY.
				available.acquire();
				//Iterator MyIterator=Tasks.iterator();
				Object o=null;
				//if(MyIterator.hasNext()){
				if(Tasks.size()>0){
					//o=MyIterator.next();
					o=Tasks.get(0);
					if((Loaded&&!LOAD_FAILURE)||!(o instanceof ApplicationData)){
						//MyIterator.remove();
						Tasks.remove(0);
					}
				}
				available.release();
				
				if(!countDown||MyTime.getCurrentTime()-countDownStart<COUNTDOWN_LENGTH)//Has a coundown taken place and the server timed out?
				if(o!=null){
					processQueuedItem(o,startTime);
				}

				
			}catch(Exception e){
				e.printStackTrace();
			}
			runLoopMaintenance(startTime);
        }
        System.out.println("Stopping thread"+ip);
    }
	
	/**
	Generate the CAPTCHA image. (100x20)
	*/
	public static final Object[] generateImage(){
		CaptchaChallenge challenge = new ComputerSessionService().generateCaptcha();
		return new Object[]{challenge.getPixels(), challenge.getKey()};
	}
	

	private void processQueuedItem(Object o,long startTime){
					//IF THIS IS A TASK EXECUTE IT.
					if(o instanceof Task){
						Task T=(Task)o;
						T.execute();
					}else//IT MUST BE A REMOTE FUNCTION CALL.
										
					//This is some class of remotely delivered function call.
					if(o instanceof ApplicationData&&Loaded){	
					
														
						boolean checkedWatch=false;
						ApplicationData MyApplicationData=(ApplicationData)o;
						String function=MyApplicationData.getFunction();//Get function name.
						int port=MyApplicationData.getPort();//Get target port.
						
						updateApplicationActivity(MyApplicationData,function);
						port=normalizeTransferPort(MyApplicationData,function,port);
						maybeLogIncomingMessage(MyApplicationData,function);
						
							if(commandDispatcher==null)
								buildFunctionHash();
							
							boolean handledByDispatcher=commandDispatcher!=null&&LegacyRunLoopApplicationDataRouter.dispatch(this,MyApplicationData,port,commandDispatcher);
							
							if(handledByDispatcher){
							}else
						
					
						//changes watch type
						if(function.equals("changewatchtype")){
							int target_watch=(Integer)((Integer[])MyApplicationData.getParameters())[0];
							int new_type=(Integer)((Integer[])MyApplicationData.getParameters())[1];
								
							if(target_watch<MyWatchHandler.getWatches().size()){
								Watch MyWatch=(Watch)MyWatchHandler.getWatch(target_watch);
								MyWatch.setType(new_type);
								MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
							}
						}else
						
						//Removes a fire wall from a port.
						if(function.equals("deletefirewall")){
							float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
							if(getCPULoad()<=maxCPU){
							
								int target_port=(Integer)(MyApplicationData.getParameters());
								
								Iterator PortIterator=Ports.entrySet().iterator();
								while(PortIterator.hasNext()){
									Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
									if(TempPort.getNumber()==target_port){
									/*	FireWall NoFireWall=new FireWall(MyComputerHandler);
										NoFireWall.setType(0);
										NoFireWall.setParentPort(TempPort);*/
										
										if(MyFileSystem.getSpaceLeft()>0){
											HackerFile installedAlready = TempPort.getFireWall().getHackerFile();
											installedAlready = checkRename(installedAlready,"");
											installedAlready.setLocation("");
											if(installedAlready.getQuantity()==0){
												installedAlready.setQuantity(1);
											}
											MyFileSystem.addFile(installedAlready,false);
											TempPort.setFireWall(NewFireWall.createNoneFirewall());
											addMessage(MessageHandler.REMOVE_FIREWALL_SUCCESS,new Object[]{installedAlready.getName()});
										}else{
											addMessage(MessageHandler.REMOVE_FIREWALL_FAIL_HD_FULL);
										}
										break;
									}
								}

								MyComputerHandler.addData(new ApplicationData("fetchports",null,0,ip),ip);
							}
						}else
						
						//INSTALL A FIRE WALL.
						if(function.equals("installfirewall")){
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							//System.out.println("Installing Firewall "+name);
							HackerFile HF=this.getFileSystem().getFile(path,name);
		
							if(HF!=null){
								int equipLevel = Integer.parseInt((String)HF.getContent().get("equip_level"));
								if(getLevel((float)(Float)Stats.get("FireWall"))>=equipLevel){
									float cpuCheck=getCPULoad()+HF.getCPUCost();
									float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
									if(cpuCheck<=maxCPU){
									
										//int FireWallType=new Integer(((String)HF.getContent().get("data")));
										
										Port P=null;
									
										Iterator PortIterator=Ports.entrySet().iterator();
										while(PortIterator.hasNext()){
											Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
											if(TempPort.getNumber()==port){
												P=TempPort;
												break;
											}
										}
										
										if(P!=null){
											
											HF.setQuantity(HF.getQuantity()-1);
											if(HF.getQuantity()<=0){
												this.getFileSystem().deleteFile(path,name);
											}
											HackerFile installedAlready = P.getFireWall().getHackerFile();
											if(!installedAlready.getName().equals("None")){
												installedAlready = checkRename(installedAlready,"");
												installedAlready.setLocation("");
												if(installedAlready.getQuantity()==0){
													installedAlready.setQuantity(1);
												}
												MyFileSystem.addFile(installedAlready,false);
												P.setFireWall(HF);
												addMessage(MessageHandler.FIREWALL_REPLACED,new Object[]{installedAlready.getName()});
											}
											else{
												P.setFireWall(HF);
											}
										}
									}
									else{
										addMessage(MessageHandler.CPU_TOO_HIGH);
										
									}
								}else{
									addMessage(MessageHandler.INSTALL_FIREWALL_FAIL_LEVEL,new Object[]{equipLevel});
								}
							}
							MyComputerHandler.addData(new ApplicationData("fetchports",null,0,ip),ip);
						}else
						//CHECK IN-COMMING CLUE DATA.
						if(function.equals("cluedata")){
							String data=(String)MyApplicationData.getParameters();
					//		MyMakeClue.checkClue(this,data,MakeClue.READ);
						}else
						
						//CONFIRM HTTP SET FOR BOUNTY.
						if(function.equals("bountyhttp")){
							lastBountyHTTP=(String)MyApplicationData.getParameters();
						}else
						
						//RE-INSTALL APPLICATION (create port)
						if(function.equals("replaceapplication")){
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
						
							HackerFile HF=this.getFileSystem().getFile(path,name);
		
							if(HF!=null){
							
								Port P=null;
							
								Iterator PortIterator=Ports.entrySet().iterator();
								while(PortIterator.hasNext()){
									Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
									if(TempPort.getNumber()==port){
										P=TempPort;
										break;
									}
								}

								float cpuCheck=getCPULoad()+HF.getCPUCost()-P.getBaseCPUCost();
								float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
								if(cpuCheck<=maxCPU){
								
									if(P!=null){//Make sure the player is allowed to remove the port at this time.
										boolean allow=true;
										Object[] Message = null;
										
										if(!P.getAccessing().equals("")){
											allow=false;
											Message=MessageHandler.REPLACE_APPLICATION_UNDER_ATTACK;
										}if(P.getAttacking()){
											allow=false;
											Message=MessageHandler.REPLACE_APPLICATION_ATTACKING;
										}if(P.getOverHeated()){
											allow=false;
											Message=MessageHandler.REPLACE_APPLICATION_OVERHEATED;
										}
										
										if(!allow){
											addMessage(Message);
											P=null;
										}							
									}
									
									if(P!=null){
										
										HF.setQuantity(HF.getQuantity()-1);
										if(HF.getQuantity()<=0){
											this.getFileSystem().deleteFile(path,name);
										}
										
										//File Acquired.
										int PortType=HF.getPortType();
										if(PortType>=0){
											P.setType(PortType);
                                            P.setMaliciousTarget(ip);
											HashMap Script=HF.getContent();
											Program NewProgram=null;
											P.setCPUCost(HF.getCPUCost());
											
											if(PortType==Port.ATTACK){
												NewProgram=new AttackProgram(this, MyComputerHandler, P, Choices,MyMakeBounty);
											}else
											
											if(PortType==Port.SHIPPING){
												NewProgram=new ShippingProgram(this, MyComputerHandler, P);
											}else
											
											if(PortType==Port.BANKING){
												NewProgram=new Banking(this, MyComputerHandler, P);
											}else
											
											if(PortType==Port.FTP){
												NewProgram=new FTPProgram(this, MyComputerHandler, MyFileSystem,P);
											}else
																						
											if(PortType==Port.HTTP){
												adRevenueTarget=ip;//Target that daily pay should be placed in (may be malicious).
												storeRevenueTarget=ip;//Target that daily store revenue should be placed in (may be malicious).
												NewProgram=new HTTPProgram(this,MyComputerHandler);
												dailyPayReduction=1.0f;
											}
											
											//if(PortType!=Port.HTTP){
												NewProgram.installScript(Script);
												P.setProgram(NewProgram);
											//}
											
											addMessage(MessageHandler.REPLACE_APPLICATION_SUCCESS);
										}
									}
								}else
									addMessage(MessageHandler.CPU_TOO_HIGH);
							}
							MyComputerHandler.addData(new ApplicationData("fetchports",null,0,ip),ip);
						}else
						
						//INSTALL A WATCH.
						if(function.equals("installwatch")){
							Object Parameter[]=(Object[])MyApplicationData.getParameters();
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							int type=(Integer)((Object[])MyApplicationData.getParameters())[2];
						
							HackerFile HF=this.getFileSystem().getFile(path,name);
							
							if(MyWatchHandler.getWatches().size()<21){
								if(HF!=null&&HF.getType()==HF.WATCH_COMPILED){
								

									float cpuCheck=getCPULoad()+HF.getCPUCost();
									float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
									if(cpuCheck<=maxCPU){
										
										HF.setQuantity(HF.getQuantity()-1);
										if(HF.getQuantity()<=0){
											this.getFileSystem().deleteFile(path,name);
										}
										
										
										Watch twatch=new Watch(this);
										twatch.setType(type);
										
										
										twatch.setSearchFireWall(0);
										twatch.setCPUCost(HF.getCPUCost());
                                        // set the watch note to be the filename by default.
										twatch.setNote(name);
										twatch.setOn(false);
										twatch.setQuantity(0.0f);
						
										if(twatch.getType()==Watch.PETTY_CASH){//Make sure initial quantities are correct.
											twatch.setInitialQuantity(getPettyCash());
										}else if(twatch.getType()==Watch.HEALTH){
											twatch.setInitialQuantity(100.0f);
										}
										
										twatch.setPort(MyApplicationData.getPort());
										HashMap Script=HF.getContent();
										Program MyProgram=new WatchProgram(this,MyComputerHandler,twatch);
										MyProgram.setComputerHandler(MyComputerHandler);
										MyProgram.installScript(Script);
										twatch.setProgram(MyProgram);
										MyWatchHandler.addWatch(twatch);
									}else
										addMessage(MessageHandler.CPU_TOO_HIGH);
								}else
									addMessage(MessageHandler.FILE_NOT_FOUND);
							}else{
								addMessage(MessageHandler.MAX_WATCHES_REACHED);
							}
							
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//INSTALL APPLICATION (create port)
						if(function.equals("installapplication")){
						
						
							if(port<MEMORY_CHART[memorytype]){
						
								String path=(String)((Object[])MyApplicationData.getParameters())[0];
								String name=(String)((Object[])MyApplicationData.getParameters())[1];
							
								HackerFile HF=this.getFileSystem().getFile(path,name);
																
								if(HF!=null){
						
									float cpuCheck=getCPULoad()+HF.getCPUCost();
									float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
									
									if(cpuCheck<=maxCPU){
											
										HF.setQuantity(HF.getQuantity()-1);
										if(HF.getQuantity()<=0){
											this.getFileSystem().deleteFile(path,name);
										}
										
										//File Acquired.
										int PortType=HF.getPortType();
										if(PortType>=0){
											Port P=new Port(this,MyComputerHandler);
											P.setNumber(port);
											P.setType(PortType);
											P.setHealth(100.0f);
											P.setCPUCost(HF.getCPUCost());
											P.setNote("");
											NewFireWall F=new NewFireWall(MyComputerHandler);
                                            F.loadHackerFile(NewFireWall.createNoneFirewall());
                                            
                                  
											F.setParentPort(P);
											P.setFireWall(F);
											P.setDummy(false);
											P.setOn(true);
											P.setMaliciousTarget(ip);
											HashMap Script=HF.getContent();
											Program NewProgram=null;
											if(PortType==Port.ATTACK){
												if(defaultAttack==0)
													defaultAttack=port;
												NewProgram=new AttackProgram(this, MyComputerHandler, P, Choices,MyMakeBounty);
											}else
											
											if(PortType==Port.SHIPPING){
												if(defaultShipping==0)
													defaultShipping=port;
												NewProgram=new ShippingProgram(this, MyComputerHandler, P);
											}else
											
											if(PortType==Port.BANKING){
												if(defaultBank==0)
													defaultBank=port;
												NewProgram=new Banking(this, MyComputerHandler, P);
											}else
											
											if(PortType==Port.FTP){
												if(defaultFTP==0)
													defaultFTP=port;
												NewProgram=new FTPProgram(this, MyComputerHandler, MyFileSystem,P);
											}
											
											if(PortType==Port.HTTP){
												if(defaultHTTP==0)
													defaultHTTP=port;
												adRevenueTarget=ip;//Target that daily pay should be placed in (may be malicious).
												storeRevenueTarget=ip;//Target that daily store revenue should be placed in (may be malicious).
												NewProgram=new HTTPProgram(this,MyComputerHandler);
											}
											
											if(NewProgram!=null){
												NewProgram.installScript(Script);
												P.setProgram(NewProgram);
											}
											Ports.put(new Integer(port),P);
										}
									}else
										addMessage(MessageHandler.CPU_TOO_HIGH);
								}else
									addMessage(MessageHandler.APPLICATION_NOT_FOUND);
									
								MyComputerHandler.addData(new ApplicationData("fetchports",null,0,ip),ip);
							}else{
								systemChange=true;
								addMessage(MessageHandler.MAX_PROGRAMS_REACHED);
							}
						}else
						
						//COMPUTER RECEIVED FIREWALL XP.
						if(function.equals("firewallxp")){
							healthChange=true;
							float mult = 1.0f;
							Float amount=(Float)MyApplicationData.getParameters();
							if(amount > 110.0f) amount = 110.0f;
							amount *= mult;
							amount+=(Float)Stats.get("FireWall");
							if(amount < 300.0f) amount = 300.0f;
							Stats.put("FireWall",amount);
						}else
						
						//Sent when an opponent has been dealt damage to provide information about their current state.
						if(function.equals("opponentupdate")){
							Float F[]=(Float[])(((Object[])MyApplicationData.getParameters())[0]);
							boolean targetWatch=(Boolean)(((Object[])MyApplicationData.getParameters())[1]);
							boolean damageFromFireWall = (Boolean)(((Object[])MyApplicationData.getParameters())[3]);
							boolean mining = (Boolean)(((Object[])MyApplicationData.getParameters())[4]);
							Float amount=F[0];
							Port P=(Port)Ports.get(new Integer(port));
							if(connectionID!=-1){
								//System.out.println("Adding Damage from opponentupdate");
								Damage.add(new Object[]{getWindowHandle(P),new Float((float)amount),damageFromFireWall,mining});
							}
							healthChange=true;

							//PACKET INCLUDES CURRENT HP/PETTY CASH OF PORT BEING ATTACKED.
							
							if(P!=null){
								P.setTargetHP(F[1]);
								P.setTargetPettyCash(F[2]);
								P.setTargetCPUCost(F[3]);
								P.setTargetWatch(targetWatch);
							}
						}else
						
						//COMPUTER HAS RECEIVED SOME ATTACK XP.
						if(function.equals("attackxp")){
							if(MyApplicationData.getParameters() instanceof Object[]){
								Float F[]=(Float[])(((Object[])MyApplicationData.getParameters())[0]);
								boolean targetWatch=(Boolean)(((Object[])MyApplicationData.getParameters())[1]);
								Float amount=F[0];
								float CastMe=(float)F[0];
								float mult = 1.0f;
								amount *= mult;
								amount+=(Float)Stats.get("Attack");
								if(amount < 300.0f && mult < 0) amount = 300.0f;
								Port P=(Port)Ports.get(new Integer(port));
								if(((Object[])MyApplicationData.getParameters()).length==4){
									
									if(connectionID!=-1){
										boolean firewall = (Boolean)(((Object[])MyApplicationData.getParameters())[2]);
										boolean mining = (Boolean)(((Object[])MyApplicationData.getParameters())[3]);
										//System.out.println("Adding Damage from attackxp");
										Damage.add(new Object[]{getWindowHandle(P),new Float((float)F[4]),firewall,mining});
									}
									//PACKET INCLUDES CURRENT HP/PETTY CASH OF PORT BEING ATTACKED.
									
									if(P!=null){
										P.setTargetHP(F[1]);
										P.setTargetPettyCash(F[2]);
										P.setTargetCPUCost(F[3]);
										P.setTargetWatch(targetWatch);
									}
								}else if(connectionID!=-1){
									boolean firewall = (Boolean)(((Object[])MyApplicationData.getParameters())[3]);
									//System.out.println("Adding Damage from attackxp else");
									Damage.add(new Object[]{getWindowHandle(P),new Float((float)F[4]),(String)(((Object[])MyApplicationData.getParameters())[2]),firewall,false});
								}
								
								if(type!=NPC)
									Stats.put("Attack",amount);
							
							}else{
								Float amount=(Float)MyApplicationData.getParameters();
								float mult = 1.0f;
								amount *= mult;
								amount+=(Float)Stats.get("Attack");
								if(amount < 300.0f && mult < 0) amount = 300.0f;
								if(type!=NPC)
									Stats.put("Attack",amount);
							}
							healthChange=true;
						}else
						
						//COMPUTER HAS RECEIVED COMMODITY XP.
						if(function.equals("miningdamageupdate")){//We have been given banking XP.
							if(MyApplicationData.getParameters() instanceof Object[]){
								Float F[]=(Float[])(((Object[])MyApplicationData.getParameters())[0]);
								boolean targetWatch=(Boolean)(((Object[])MyApplicationData.getParameters())[1]);
								Float amount=F[4];
								Port P=(Port)Ports.get(new Integer(port));
								if(((Object[])MyApplicationData.getParameters()).length==4){
									if(connectionID!=-1){
										boolean firewall = (Boolean)(((Object[])MyApplicationData.getParameters())[2]);
										//System.out.println("Adding Damage from miningdamageupdate");
										Damage.add(new Object[]{getWindowHandle(P),amount,firewall,true});
									}
									//PACKET INCLUDES CURRENT HP/PETTY CASH OF PORT BEING ATTACKED.
									if(P!=null){
										P.setTargetHP(F[1]);
										P.setTargetPettyCash(F[2]);
										P.setTargetCPUCost(F[3]);
										P.setTargetWatch(targetWatch);
									}
								}else if(connectionID!=-1){
									boolean firewall = (Boolean)(((Object[])MyApplicationData.getParameters())[2]);
									//System.out.println("Adding Damage from miningdamageupdate else");
									Damage.add(new Object[]{getWindowHandle(P),amount,(String)(((Object[])MyApplicationData.getParameters())[2]),firewall,true});
								}
							
							}
							healthChange=true;
						}else
						
						//A DIRECTORY LISTING HAS BEEN REQUESTED FROM THE FILE SYSTEM.
						if(function.equals("requestdirectory")){//Request a directory listing.
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							//System.out.println("Path is: "+path);
							Object O[]=MyFileSystem.getDirectory(path);//Update the value to take into account ID.
							Object Temp[]=new Object[O.length+1];
							Temp[0]=(Integer)((Object[])MyApplicationData.getParameters())[1];
							for(int i=0;i<O.length;i++)
								Temp[i+1]=O[i];
							O=Temp;
						
							PA.setDirectory(O);
							
							systemChange=true;
						}else
						
						//REQUEST AN EQUIPMENT UPDATE
						if(function.equals("requestequipment")){//Request a directory listing.
							Object O[]=MyFileSystem.getEquipment("");//Update the value to take into account ID.
							Object Temp[]=new Object[O.length+1];
							Temp[0]=MyApplicationData.getParameters();
							for(int i=0;i<O.length;i++){
								if(O[i]!=null)
									MyEquipmentSheet.describeCard((HackerFile)O[i]);//Testing outputting a description of the bonus.
								Temp[i+1]=O[i];
							}
							O=Temp;
							PA.setDirectory(O);
							
							O=MyEquipmentSheet.getEquipment();//Update the value to take into account ID.
							Temp=new Object[O.length+1];
							Temp[0]=MyApplicationData.getParameters();
							for(int i=0;i<O.length;i++){
								if(O[i]!=null)
									MyEquipmentSheet.describeCard((HackerFile)O[i]);//Testing outputting a description of the bonus.
								Temp[i+1]=O[i];
							}
							O=Temp;
							
							PA.setSecondaryDirectory(O);
							systemChange=true;
						}else
						
						//Install equipment.
						if(function.equals("installequipment")){//Request a directory listing.
							int position=(Integer)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							MyEquipmentSheet.equip(position,name);
							
							Object O[]=MyFileSystem.getEquipment("");//Update the value to take into account ID.
							Object Temp[]=new Object[O.length+1];
							Temp[0]=(Integer)((Object[])MyApplicationData.getParameters())[2];
							for(int i=0;i<O.length;i++)
								Temp[i+1]=O[i];
							O=Temp;
							PA.setDirectory(O);
							
							O=MyEquipmentSheet.getEquipment();//Update the value to take into account ID.
							Temp=new Object[O.length+1];
							Temp[0]=(Integer)((Object[])MyApplicationData.getParameters())[2];
							for(int i=0;i<O.length;i++)
								Temp[i+1]=O[i];
							O=Temp;
							
							PA.setSecondaryDirectory(O);
							systemChange=true;
						}else
						
						//Repair equipment.
						if(function.equals("repairequipment")){//Request a directory listing.
						
							int position=(Integer)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							
							if(position!=-1)
								MyEquipmentSheet.repair(position);
							else{
								HackerFile Equipment=MyFileSystem.getFile("",name);
								if(Equipment!=null)
									MyEquipmentSheet.repair(Equipment);
							}
							
							MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
						}else
						
						//FINALIZE THE REMOTE DIRECTORY REQUEST.
						if(function.equals("delivereddirectory")){
							Object[] paramss = (Object[])MyApplicationData.getParameters();
							Object Directory[] = null;
							if(paramss.length==1) {
								Directory=(Object[])MyApplicationData.getParameters();
							}
							else if(paramss.length==2) {
								Directory=(Object[])paramss[0];
								boolean npcBool = (Boolean)paramss[1];
								PA.setAllowedDir(!npcBool || isNPC());
							}
							PA.setSecondaryDirectory(Directory);
							systemChange=true;
						}else
						
						//A FILE HAS BEEN REQUESTED FROM THE FILE SYSTEM.
						if(function.equals("requestfile")){//Request a file.
							String path=((String[])MyApplicationData.getParameters())[0];
							String name=((String[])MyApplicationData.getParameters())[1];
							HackerFile HF=MyFileSystem.getFile(path,name);
							if(HF!=null){
								if(HF.getType()==HackerFile.GAME||HF.getType()==HackerFile.GAME_PROJECT){
									HF=HF.clone();
									HF.setContent(null);
								}
							}
							PA.setFile(HF);
							systemChange=true;
						}else
						
						//A FILE HAS BEEN REQUESTED FROM THE FILE SYSTEM.
						if(function.equals("requestgame")){//Request a file.
							String path=((String[])MyApplicationData.getParameters())[0];
							String name=((String[])MyApplicationData.getParameters())[1];
							HackerFile HF=MyFileSystem.getFile(path,name);
							
							HashMap LoadFile=new HashMap();
							HackerFile SaveFile=MyFileSystem.getFile("",name+".save");
							if(SaveFile!=null){
								String data=(String)SaveFile.getContent().get("data");
								if(data!=null){
									String Entries[]=data.split("\n");
									try{
										for(int i=0;i<Entries.length;i++){
											String Data[]=Entries[i].split("\t");
											String key=Data[0];
											String type=Data[1];
											Variable MyVariable=null;
											if(type.equals("string")){
												MyVariable=new TypeString(Data[2]);
											}else
											
											if(type.equals("bool")){
												MyVariable=new TypeBoolean(new Boolean(Data[2]));
											}else
											
											if(type.equals("int")){
												MyVariable=new TypeInteger(new Integer(Data[2]));
											}else
											
											if(type.equals("float")){
												MyVariable=new TypeFloat(new Float(Data[2]));
											}
											
											if(MyVariable!=null){
												LoadFile.put(key,MyVariable);
											}
										}
									}catch(Exception e){
									
									}
								}
							}
							PA.setLoadFile(LoadFile);
							PA.setFile(HF);
							systemChange=true;
						}else
						
						
						//SET THE DESCRIPTION ON A FILE.
						if(function.equals("setfiledescription")){//Request a file.
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							String description=(String)((Object[])MyApplicationData.getParameters())[2];
							HackerFile MyFile=MyFileSystem.getFile(path,name);
							if(MyFile!=null&&MyFile.getType()!=HackerFile.CLUE){
								MyFile.setDescription(description);
								PA.setFile(MyFile);
							}
							systemChange=true;
						}else
					
						//SET THE PRICE ON A FILE.
						if(function.equals("setfileprice")){//Request a file.
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							Float price=(Float)((Object[])MyApplicationData.getParameters())[2];
							HackerFile MyFile=MyFileSystem.getFile(path,name);
							if(MyFile!=null){
								MyFile.setPrice(price);
								PA.setFile(MyFile);
							}
							systemChange=true;
						}else
						
						//Have we received some walking information from the 3D chat?
						if(function.equals("hacktendoTarget")){
							int targetX=(Integer)((Object[])MyApplicationData.getParameters())[0];
							int targetY=(Integer)((Object[])MyApplicationData.getParameters())[1];
							int currentX=(Integer)((Object[])MyApplicationData.getParameters())[2];
							int currentY=(Integer)((Object[])MyApplicationData.getParameters())[3];
							//WorldSingleton.getInstance().addTargetData("game",ip,targetX,targetY,currentX,currentY);
						}else
						
						//Have we received some walking information from the 3D chat?
						if(function.equals("hacktendoActivate")){
							System.out.println("Attempting to activate an object.");
							int activateID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							int activateType=(Integer)((Object[])MyApplicationData.getParameters())[1];
							//WorldSingleton.getInstance().addActivationData("game",ip,activateID,activateType);
						}else
						
						//WE HAVE BEEN ASKED TO DELETE A FILE FROM THE FILE SYSTEM.
						if(function.equals("deletefile")){
						
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String name=(String)((Object[])MyApplicationData.getParameters())[1];
							
							MyFileSystem.deleteFile(path,name);
							PA.setRequestPrimary(true,1);
							systemChange=true;
						}else
                        
                        if(function.equals("deletemulti")) {
                            Object[] allFiles = (Object[])((Object[])MyApplicationData.getParameters())[0];
                            String path = "";
                            String name = "";
                            for (int i=0; i < allFiles.length; i++) {
                                String[] file = (String[])allFiles[i];
                                path = file[0];
                                name = file[1];
                                if ( (file[2]).equals("directory") ) {
                                    MyFileSystem.deleteDirectory(path + "/" + name + "/");
                                } else {
                                    MyFileSystem.deleteFile(path,name);
                                }
                            }
                            PA.setRequestPrimary(true,1);
							systemChange=true;
                        }else
						
						//DECOMPILE A FILE.
						if(function.equals("decompilefile")){
							String path=(String)((Object[])MyApplicationData.getParameters())[0];
							String fileName = (String)((Object[])MyApplicationData.getParameters())[1];
							HackerFile HFCheck=MyFileSystem.getFile(path,fileName);
                            HackerFile HF = HFCheck.clone();
							if(HFCheck.getTypeString().equals("compiled")&&(HFCheck.getMaker().toUpperCase().equals(userName.toUpperCase()))){
								if(HFCheck!=null){
									float compilePrice=(Float)((Object[])MyApplicationData.getParameters())[2];
                                    HashMap levels = new HashMap();
                                    levels.put("Attack",new Integer(100));
                                    levels.put("Merchanting",new Integer(100));
                                    levels.put("Watch",new Integer(100));
	                                    try{
	                                    Object[] params = new Object[]{new Integer(HFCheck.getType()),HFCheck.getContent(),levels};
	                                    HashMap result = (HashMap) sessionService.executeRemote(LocalWebConfig.getXmlRpcUrl(),"hackerRPC.compileApplication",params);
	                                    if(result!=null&&((String)(result.get("error"))).length()==0){
	                                        compilePrice = (float)(double)(Double)result.get("price");
	                                    }
	                                    }catch(Exception e){
	                                    }
									HFCheck.setQuantity(HFCheck.getQuantity()-1);
									if(HFCheck.getQuantity()<=0){
										MyFileSystem.deleteFile(path,HFCheck.getName());
									}

									float xp=compilePrice/100.0f;//XP = 1/10 The Compile Price.
									String xp_type="";
									
									if(HFCheck.getType()==HFCheck.BANKING_COMPILED)
										xp_type="bankxp";
									else

									if(HFCheck.getType()==HFCheck.ATTACKING_COMPILED)
										xp_type="attackxp";
									else
									
									if(HFCheck.getType()==HFCheck.SHIPPING_COMPILED)
										xp_type="redirectxp";
									else
								
									if(HFCheck.getType()==HFCheck.HTTP)
										xp_type="httpxp";
									else
									
									if(HFCheck.getType()==HFCheck.WATCH_COMPILED)
										xp_type="watchxp";
										
									MyComputerHandler.addData(new ApplicationData(xp_type,new Float((-1.0f)*xp),0,ip),ip);
									
									if(HF.getType()!=HF.HTTP)
										HF.setType(HF.getType()+1);
									if(HFCheck.getType()!=HFCheck.HTTP_SCRIPT)
										HF.setType(HFCheck.getType()+1);
									else
										HF.setType(HF.HTTP_SCRIPT);
									HF.setName(HFCheck.getName().replaceAll("\\.bin",""));
									MyComputerHandler.addData(new ApplicationData("pettycash",new Float(compilePrice),0,ip),ip);
									//MyComputerHandler.addData(new ApplicationData("savefile",MyApplicationData.getParameters(),0,ip),ip);
                                    saveFile(HF,HF,path);
								}
							}
						}else
						
                        if (function.equals("sellfilemulti")) {
                            Object[] allFiles = (Object[])((Object[])MyApplicationData.getParameters())[0];
                            String ip = (String)((Object[])MyApplicationData.getParameters())[1];
                            String path = "";
                            String name = "";
                            String maker = "";
                            Integer quantity = 0;
                            float compileCost = 0.0f;
                            float totalPay = 0.0f;
                            HashMap levels = new HashMap();
                            levels.put("Attack",new Integer(100));
                            levels.put("Merchanting",new Integer(100));
                            levels.put("Watch",new Integer(100));
                            if(checkBank()){
                                for (int i=0; i < allFiles.length; i++) {
                                    Object[] file = (Object[])allFiles[i];
                                    path = (String)file[0];
                                    name = (String)file[1];
                                    maker = (String)file[2];
                                    quantity = (Integer)file[3];
                                    // calculate the total $ to send to the player
                                    // remove the files from their HD
                                    // add the files to the store
                                    // get the hacker file associated with this file
                                    HackerFile HF = MyFileSystem.getFile(path,name);
									if(HF.getType()!=HackerFile.NEW_FIREWALL){
										totalPay +=  (Float)makers.get(maker)*quantity;
									}
									else{
										//System.out.println("Selling a firewall");
										HashMap content = HF.getContent();
										Object price = content.get("store_price");
										if(price != null){
											float firewallPrice = new Float(""+price);
										//	System.out.println("Sellig it for "+firewallPrice);
											totalPay += firewallPrice;
										}
									}
                                    if(HF!=null&&HF.getQuantity()>=quantity){
                                        HF.setQuantity(HF.getQuantity()-quantity);
                                        if(HF.getQuantity()<=0){
                                            MyFileSystem.deleteFile(path,HF.getName());
                                        }
                                        
                                        Object O[]=new Object[]{path,HF.clone(),compileCost,ip,quantity};
                                        MyComputerHandler.addData(new ApplicationData("sellfile",O,0,"store"+getServerID()),store);
                                    }
                                }
                                //MyComputerHandler.addData(new ApplicationData("pettycash",totalPay,0,ip),ip);
                                pettyCash+=totalPay;
                            }else{
                                //BANK PORT MESSAGE
                                addMessage(MessageHandler.SELL_FAIL_BANK_PORT);
                            }
                            PA.setRequestPrimary(true,1);
                            systemChange=true;
                        } else
                        
                        if(function.equals("savefile")){
                            Object[] parameters = (Object[])MyApplicationData.getParameters();
                            String path=(String)parameters[0];
							HackerFile HF=(HackerFile)parameters[1];
                            HackerFile HFCheck=MyFileSystem.getFile(path,HF.getName());
                            boolean stolenFile = false;
							String stolenFromIP = "";
							int stolenFromPort = -1;
							if(parameters.length==4){
								if(parameters[2] instanceof String){
									stolenFile = true;
									stolenFromIP = (String)parameters[2];
									stolenFromPort = (Integer)parameters[3];
								}
							}
                            
                            saveFile(HF,HFCheck,path);
							if(stolenFile){
                                addMessage(MessageHandler.FILE_SUCCESSFULLY_STOLEN,new Object[]{HF.getName(),stolenFromIP},new Object[]{stolenFromPort,stolenFromIP});
								addMessage(MessageHandler.FILE_SUCCESSFULLY_STOLEN_GAME,new Object[]{HF.getName(),stolenFromIP});
                            }
							if(HF.getType()==HF.PCI||HF.getType()==HF.AGP){
								PA.setRequestHardware(true);
							}
                        
                        }else
                        
                        if(function.equals("compilefile")){
                            boolean success=true;
							Object[] parameters = (Object[])MyApplicationData.getParameters();
							String path=(String)parameters[0];
							HackerFile HF=(HackerFile)parameters[1];
							HackerFile HFCheck=MyFileSystem.getFile(path,HF.getName());
                            
                            float price=(Float)((Object[])MyApplicationData.getParameters())[2];

							//Check to make sure levels match.
                            HashMap PlayerLevels=new HashMap();
                            PlayerLevels.put("Attack",new Integer(getLevel((float)(Float)Stats.get("Attack"))));
                            PlayerLevels.put("Merchanting",new Integer(getLevel((float)(Float)Stats.get("Bank"))));
                            PlayerLevels.put("Watch",new Integer(getLevel((float)(Float)Stats.get("Watch"))));
                            PlayerLevels.put("HTTP",new Integer(getLevel((float)(Float)Stats.get("Webdesign"))));
                            PlayerLevels.put("Redirecting",new Integer(getLevel((float)(Float)Stats.get("Redirecting"))));


	                            try{
	                                Object[] params = new Object[]{new Integer(HF.getType()),HF.getContent(),PlayerLevels};
	                                HashMap result = (HashMap) sessionService.executeRemote(LocalWebConfig.getXmlRpcUrl(),"hackerRPC.compileApplication",params);
	                                if(result!=null&&((String)(result.get("error"))).length()==0){
	                                    float cpuCost = (float)(double)(Double)result.get("cpucost");
	                                    price = (float)(double)(Double)result.get("price");
	                                    HF.setCPUCost(cpuCost);
	                                }
	                            }catch(Exception e){
	                                success=false;
	                            }
                        
                            //Check to make sure our bank is on.
                            if(!checkBank()){
                                success=false;
                                addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
                            }
                        
                            //Check to make sure the two files are identical.
                            if(HFCheck!=null){
                                if(HF.checkSumFailed(HFCheck)){
                                    success=false;
                                    addMessage(MessageHandler.FILE_CHANGED_SINCE_LAST_SAVE);
                                }
                            }
                        
                            if(pettyCash<price){
                                success=false;
                                addMessage(MessageHandler.COMPILE_FAIL_NOT_ENOUGH_MONEY);
                            }else if((MyFileSystem.getSpaceLeft()<1)&&HFCheck==null){//Not enough disk space to compile file.
                                success=false;
                                addMessage(MessageHandler.COMPILE_FAIL_HD_FULL);
                            }else if(success){
                                if(HF.getType()!=HF.FTP_COMPILED){//Provide XP for the program that has been compiled.

                                    float xp=price/100.0f;//XP = 1/10 The Compile Price.
                                    String xp_type="";
                                    
                                    if(HF.getType()==HF.BANKING_COMPILED)
                                        xp_type="bankxp";
                                    else

                                    if(HF.getType()==HF.ATTACKING_COMPILED)
                                        xp_type="attackxp";
                                    else
                                    
                                    if(HF.getType()==HF.SHIPPING_COMPILED)
                                        xp_type="redirectxp";
                                    else
                                    
                                    if(HF.getType()==HF.WATCH_COMPILED)
                                        xp_type="watchxp";
                                    else
                                    
                                    if(HF.getType()==HF.HTTP){
                                        xp_type="httpxp";
                                    }
                                        
                                    MyComputerHandler.addData(new ApplicationData(xp_type,new Float(xp),0,ip),ip);
                                }
                                MyComputerHandler.addData(new ApplicationData("pettycash",new Float(price*-1.0),0,ip),ip);
				HF.setMaker(userName);
                            
                            	saveFile(HF,HFCheck,path);
                            }
                            
                        }else
						//WE HAVE BEEN ASKED TO SAVE A FILE TO THE FILE SYSTEM.
						if(function.equals("sellfile")){

							//Is this hardware?
							boolean success=true;
							Object[] parameters = (Object[])MyApplicationData.getParameters();
							String path=(String)parameters[0];
							HackerFile HF=(HackerFile)parameters[1];
							HackerFile HFCheck=MyFileSystem.getFile(path,HF.getName());
							
							//IS THIS A FILE BEING SOLD BACK TO THE STORE.
							int sellQuantity=1;
							float sellPrice=0.0f;
							float minimumSellPrice=0.0f;
							if(HF.getType()!=HackerFile.NEW_FIREWALL){
								minimumSellPrice =  (Float)makers.get(HF.getMaker());
							}
                                
                            HF.setQuantity(1);
                            float compilePrice=(Float)((Object[])MyApplicationData.getParameters())[2];
                            HashMap PlayerLevels=new HashMap();
                            PlayerLevels.put("Attack",new Integer(100));
                            PlayerLevels.put("Merchanting",new Integer(100));
                            PlayerLevels.put("Watch",new Integer(100));
                            PlayerLevels.put("HTTP",new Integer(100));
                            PlayerLevels.put("Redirecting",new Integer(100));


	                            try{
	                                Object[] params = new Object[]{new Integer(HF.getType()),HF.getContent(),PlayerLevels};
	                                HashMap result = (HashMap) sessionService.executeRemote(LocalWebConfig.getXmlRpcUrl(),"hackerRPC.compileApplication",params);
	                                if(result!=null&&((String)(result.get("error"))).length()==0){
	                                    float cpuCost = (float)(double)(Double)result.get("cpucost");
	                                    compilePrice = (float)(double)(Double)result.get("price");
	                                    
	                                }
	                            }catch(Exception e){
	                                compilePrice = 0.0f;
	                            }
                            int quantity = (Integer)((Object[])MyApplicationData.getParameters())[4];
                            if(HF.getType()==HF.AGP||HF.getType()==HF.PCI){//Special case of hardware.
                                if(HF.getMaker().equals("Medium"))
                                    sellPrice=2000.0f;
                                else if(HF.getMaker().equals("High"))
                                    sellPrice=20000.0f;
                                else if(HF.getMaker().equals("Rare"))
                                    sellPrice=200000.0f;
                            }else{
                                if(HFCheck!=null)
                                    sellPrice=compilePrice*2.0f-(compilePrice*0.01f*(1.0f+HFCheck.getQuantity()));
                                else
                                    sellPrice=compilePrice*2.0f-(compilePrice*0.01f);
                            }

                            if(sellPrice<minimumSellPrice)
                                sellPrice=minimumSellPrice;
                        
    
                            String ip=(String)((Object[])MyApplicationData.getParameters())[3];
							HF.setPrice(sellPrice);
                            
                            if(HF.getType()==HackerFile.NEW_FIREWALL)
                                success=false;
						
							if(success){//Make sure we don't need to rename the file.
												
                                HF.setLocation("Store/");
								saveFile(HF,HFCheck,path);
                                
								if(HF.getType()==HF.PCI||HF.getType()==HF.AGP){
									PA.setRequestHardware(true);
								}
								
								
								
							}
							systemChange=true;
						}
                        
                        // USER HAS CHANGED THEIR PREFERENCES
                        
                        else if (function.equals("setpreferences")) {
                            HashMap preferences = (HashMap)((Object[])MyApplicationData.getParameters())[1];
/*System.out.println("SETTING Preferences...");
Iterator it = preferences.keySet().iterator();
while (it.hasNext()) {
    String n = (String)it.next();
    System.out.println("   " + n + ": " + preferences.get(n));
}*/
                            this.preferences = preferences;
                        }
                        
                        
                        //ADD A MESSAGE TO THE MESSAGE QUEUE.
                        else if(function.equals("message")){//We have received a message.
							Object messageObject = MyApplicationData.getParameters();
							if(messageObject instanceof String){
								addMessage((String)messageObject);
							}
							else if(messageObject instanceof Object[]){
								Object[] messageArray = (Object[])messageObject;
								Object[] message = (Object[])messageArray[0]; // this throws a ClassCastException when messageArray[0] is a string
								if(messageArray[1] instanceof Object[]){
									Object[] parameters = (Object[])messageArray[1];
									if(messageArray.length > 2){
										Object[] portInfo = (Object[])messageArray[2];
										addMessage(message,parameters,portInfo);
									}
									else{
										addMessage(message,parameters);
									}
								}
								else{
									addMessage(messageArray);
								}
							}
							
							systemChange=true;
						}else
						
						//SEND AN EMAIL USING XML RPC.
						if(function.equals("sendemail")){//We have received a message.
							String message=(String)MyApplicationData.getParameters();
							if(checkBank()){
									if(pettyCash>=100.0){
										try{
											Object[] params = new Object[]{ip,message};
											sessionService.executeRemote("http://www.hackwars.net/xmlrpc/mail.php","sendEmail",params);
										}catch(Exception e){
										}
						
									MyComputerHandler.addData(new ApplicationData("pettycash",-100.0f,0,ip),ip);
								}
							}
						}
                        
                        //POST TO FACEBOOK USING XML RPC.
                        else if(function.equals("sendfacebook")){//We have received a message.
							String message=(String)((Object[])MyApplicationData.getParameters())[0];
							String targetIP=(String)((Object[])MyApplicationData.getParameters())[1];

								try{
									Object[] params = new Object[]{ip,targetIP,message};
									sessionService.executeRemote("http://www.hackwars.net/xmlrpc/facebook.php","sendFacebook",params);
								}catch(Exception e){
									e.printStackTrace();
								}

						}else
						
						//POST TO FACEBOOK USING XML RPC.
						if(function.equals("facebookupdate")){//We have received a message.
								String message=(String)MyApplicationData.getParameters();
								try{	
									Object[] params = new Object[]{ip,new Double(pettyCash),new Double(bankMoney),new Integer(defaultBank)};
									sessionService.executeRemote("http://www.hackwars.net/xmlrpc/facebook.php","updateFacebook",params);
								}catch(Exception e){
									e.printStackTrace();
								}
						}else
						
						//ADD A MESSAGE TO THE MESSAGE QUEUE.
						if(function.equals("dailypayset")){//We have received a message.
							String bountyip=(String)MyApplicationData.getParameters();
							MyMakeBounty.checkBounty(this,null,MyMakeBounty.CHANGE,MyApplicationData.getSourceIP(),false,bountyip);
						}else
					
						//CHANGE AMOUNT OF MONEY IN PETTY CASH.
						if(function.equals("pettycash")){//We have been asked to modify petty cash.
							//Do we have a bank port running.
							DecimalFormat nf = new DecimalFormat("#.00");
							float value = 0.0f;
							float returnValue = 0.0f;
							boolean sendMessage = true;
							Object parameters = MyApplicationData.getParameters();
							if(parameters instanceof Float){
								value=(Float)parameters;
							}
							else if(parameters instanceof Object[]){
								Object[] params = (Object[])parameters;
								value = (Float)params[0];
								Object secondEntry = params[1];
								if(secondEntry instanceof Float){
									returnValue = (Float)params[1];
								}
								else if ( secondEntry instanceof Boolean ) {
									sendMessage = false;
								}
							}
							if(getTotalLevel()<noobLevel&&!MyApplicationData.getSourceIP().equals(ip)){
								addMessage(MessageHandler.TRANSFER_FAIL_NOOB_LEVEL,new Object[]{noobLevel});
								if(!MyApplicationData.getSourceIP().equals(ip)){
									MyComputerHandler.addData(MyApplicationData,MyApplicationData.getSourceIP());
								}
							}else if(checkBank()){
		
								setPettyCash(pettyCash+value);
								CentralLogging.getInstance().addOutput(ip+"\t"+MyApplicationData.getSourceIP()+"\t"+"1\t"+value+"\n");
								if(!MyApplicationData.getSourceIP().equals(ip)&&sendMessage){
									ApplicationData message = new ApplicationData("message",new Object[]{MessageHandler.TRANSFER_SENT_SUCCESSFUL,new Object[]{nf.format(value)}},0,MyApplicationData.getSourceIP());
									MyComputerHandler.addData(message,MyApplicationData.getSourceIP());
									addMessage(MessageHandler.TRANSFER_RECEIVED,new Object[]{nf.format(value),MyApplicationData.getSourceIP()});
								}
								if(pettyCash<0)
									pettyCash=0;
							}else{
								addMessage(MessageHandler.TRANSFER_RECEIVE_FAIL_BANK_PORT,new Object[]{nf.format(value),MyApplicationData.getSourceIP()});
								if(!MyApplicationData.getSourceIP().equals(ip)){
									ApplicationData pettyCash = new ApplicationData("pettycash",returnValue,0,MyApplicationData.getSourceIP());
									ApplicationData message = new ApplicationData("message",MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT,0,MyApplicationData.getSourceIP());
									MyComputerHandler.addData(pettyCash,MyApplicationData.getSourceIP());
									MyComputerHandler.addData(message,MyApplicationData.getSourceIP());
								}
							}
								
							if(pettyCash<respawnMoney)
								respawn(Port.BANKING);
								
							systemChange=true;

						}else
						
						//THIS FUNCTION ACCEPTS A TRANSFER OF A COMMODITY FROM MINING.
						if(function.equals("commodity")){//We have been asked to modify petty cash.
						
							if(checkShipping()){//Do we have an active shipping port?
								Object[] parameters = (Object[])MyApplicationData.getParameters();
								int commodity=(Integer)parameters[0];
								float value=(Float)parameters[1];
								int redirectPort = (Integer)parameters[2];
								Port p = (Port)Ports.get(redirectPort);
								int windowHandle = getWindowHandle(p);
								setCommodityAmount(commodity,getCommodity(commodity)+value);
								String targetIP = (String)parameters[3];
								addMessage(MessageHandler.RECEIVED_COMMODITY,new Object[]{(int)value,commodityString[commodity]},new Object[]{windowHandle,ip});
								addMessage(MessageHandler.RECEIVED_COMMODITY_GAME,new Object[]{(int)value,commodityString[commodity],targetIP});
							}else{
								addMessage(MessageHandler.RECEIVED_COMMODITY_FAIL);
								if(!MyApplicationData.getSourceIP().equals(ip)){
									MyComputerHandler.addData(MyApplicationData,MyApplicationData.getSourceIP());
								}
							}
								
							systemChange=true;

						}else
						
						//CHANGE AMOUNT OF MONEY IN BANK.
						if(function.equals("bank")){
							float value=(Float)MyApplicationData.getParameters();

							//Do we have a bank port running.
							if(checkBank()){
								bankMoney+=value;
								if(value>0)
									CentralLogging.getInstance().addOutput(ip+"\t"+MyApplicationData.getSourceIP()+"\t"+"0\t"+value+"\n");
							}else if(value>0.0f){
								addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
							}
							systemChange=true;
						}else 
						
						//UNLOCK THE LOCKED STATE IF CORRECT CAPTCHA.
						if(function.equals("unlock")){
							String code=(String)MyApplicationData.getParameters();
							if(code.equals(unlockKey)){
								lockCount=0;
								locked=false;
							}else{
								RESEND_CAPTCHA=true;
							}
						}else
						
						//RETURNS AN ARRAY OF THE WATCHES THAT ARE CURRENTLY INSTALLED ON THIS PROGRAM.
						if(function.equals("fetchwatches")){
							PacketWatch PacketWatches[]=new PacketWatch[MyWatchHandler.getWatches().size()];
							Iterator WatchIterator=MyWatchHandler.getWatches().iterator();
							int ii=0;
							while(WatchIterator.hasNext()){
								Watch TempWatch=(Watch)WatchIterator.next();
								PacketWatches[ii]=TempWatch.getPacketWatch();
								ii++;
							}
							PA.setPacketWatches(PacketWatches);
							systemChange=true;
						}else 
						
						//SHOW CHOICES CAN REQUEST THAT A SCRIPT IS INSTALLED.
						if(function.equals("requestinstallscript")){
							String targetIP=(String)((Object[])MyApplicationData.getParameters())[0];
							int targetPort=(Integer)((Object[])MyApplicationData.getParameters())[1];
							String path=(String)((Object[])MyApplicationData.getParameters())[2];
							String file=(String)((Object[])MyApplicationData.getParameters())[3];
							Object MaliciousParameters=(Object[])((Object[])MyApplicationData.getParameters())[4];
							HashMap Content=null;
							
							HackerFile HF=MyFileSystem.getFile(path,file);
							
							if(HF!=null){
								
								HF.setQuantity(HF.getQuantity()-1);
								if(HF.getQuantity()<=0){
									MyFileSystem.deleteFile(path,file);
								}
									
								Content=HF.getContent();
								
								Object O[]=new Object[]{Content,MaliciousParameters};
								MyComputerHandler.addData(new ApplicationData("installScript",O,targetPort,ip),targetIP);
							}
							systemChange=true;
						}
						
						//REQUEST YOUR OWN WEB-PAGE.
						else if(function.equals("requestpage")){
							PA.setBody(pageBody);
							PA.setTitle(pageTitle);
							systemChange=true;
						}else
						
						//CHECK FOR THE FILE AND RETURN THE FILE AND PRICE.
						if(function.equals("requestpurchase")){
							String file=(String)((Object[])MyApplicationData.getParameters())[0];
							int quantity=(Integer)((Object[])MyApplicationData.getParameters())[1];
							
							if(quantity>0){
								
								HackerFile HF=this.getFileSystem().getFile("Store/",file);
								if(!(HF==null)){
									if(HF.getQuantity()<quantity&&HF.getQuantity()!=-1)
										quantity=HF.getQuantity();
									
									HackerFile PurchasedFile=HF.clone();
									PurchasedFile.setQuantity(quantity);
									
									if(HF.getQuantity()!=-1){
									
										HF.setQuantity(HF.getQuantity()-quantity);
										if(HF.getQuantity()<=0)
											MyFileSystem.deleteFile("Store/",file);
											
									}
										
									Object O[]=new Object[]{PurchasedFile,storeRevenueTarget,new Integer(type)};
										
									MyComputerHandler.addData(new ApplicationData("continuepurchase",O,0,ip),MyApplicationData.getSourceIP());
								}else
									MyComputerHandler.addData(new ApplicationData("message",MessageHandler.PURCHASE_FAIL_FILE_NOT_FOUND,0,ip),MyApplicationData.getSourceIP());
							
							}
							systemChange=true;
						}else
						
						//IN THE NEXT ROUND OF PURCHASING WE CONFIRM THAT THE PLAYER HAS ENOUGH MONEY.
						if(function.equals("continuepurchase")){
							HackerFile HF=(HackerFile)((Object[])MyApplicationData.getParameters())[0];
							String payTarget=(String)((Object[])MyApplicationData.getParameters())[1];
							int sellerType=(Integer)((Object[])MyApplicationData.getParameters())[2];
							int quantity = HF.getQuantity();
							HackerFile HFCheck=MyFileSystem.getFile("",HF.getName());
							
							float level=0.0f;
							if(HF!=null){
								if(HF.getType()==HF.CPU){
									level=new Float((String)HF.getContent().get("level"));
								}else if(HF.getType()==HF.HD){
									level=new Float((String)HF.getContent().get("level"));
								}else if(HF.getType()==HF.FIREWALL){
									level=new Float((String)HF.getContent().get("level"));
								}else if(HF.getType()==HF.MEMORY){
									level=new Float((String)HF.getContent().get("level"));
								}
							}
							
							float totalLevel=0.0f;
							if(!(HF.getType()==HF.FIREWALL)){
								totalLevel+=getLevel((float)(Float)Stats.get("Attack"));
								totalLevel+=getLevel((float)(Float)Stats.get("Bank"));
								totalLevel+=getLevel((float)(Float)Stats.get("Watch"));
								totalLevel+=getLevel((float)(Float)Stats.get("Scanning"));
								totalLevel+=getLevel((float)(Float)Stats.get("Webdesign"));
								totalLevel+=getLevel((float)(Float)Stats.get("Redirecting"));
								totalLevel+=getLevel((float)(Float)Stats.get("Repair"));
							}
							totalLevel+=getLevel((float)(Float)Stats.get("FireWall"));

							float mult=(getLevel((float)(Float)Stats.get("Bank"))-50.0f)/100.0f;
							float price=(quantity*HF.getPrice())-(quantity*HF.getPrice()*mult);
							
							if(MyFileSystem.getSpaceLeft()<=0){
								addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL);
								Object O[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),MyApplicationData.getSourceIP());
							}else if(!checkBank()&&price>0){
								addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
								Object O[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),MyApplicationData.getSourceIP());
							}else if(pettyCash<price){
								addMessage(MessageHandler.PURCHASE_FAIL_NOT_ENOUGH_MONEY);
								Object O[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),MyApplicationData.getSourceIP());
							}else if(MyFileSystem.getSpaceLeft()<1&&!((HF.getType()==HF.CPU||HF.getType()==HF.HD||HF.getType()==HF.MEMORY)||HFCheck!=null)){
								addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL);
								Object O[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),MyApplicationData.getSourceIP());
							}else if(totalLevel<level&&sellerType==1){
								Object O[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),MyApplicationData.getSourceIP());
								addMessage(MessageHandler.PURCHASE_FAIL_NOT_HIGH_ENOUGH_LEVEL);
							}else{
								boolean buyFail=false;
								Object O[]=new Object[]{"",HF};
								if(HF.getType()==HF.CPU){
									int type=new Integer((String)HF.getContent().get("data"));
									if(CPU_CHART[type]>CPU_CHART[cputype]){
										cputype=type;
										addMessage(MessageHandler.PURCHASE_NEW_CPU,new Object[]{HF.getName()});
									}else{
										buyFail=true;
										addMessage(MessageHandler.PURCHASE_FAIL_OLDER_CPU);
									}
								}else if(HF.getType()==HF.HD){
									int type=new Integer((String)HF.getContent().get("data"));
									if(MyFileSystem.checkType(type)){
										MyFileSystem.setHDType(type);
										addMessage(MessageHandler.PURCHASE_NEW_HD,new Object[]{HF.getName()});
									}else{
										buyFail=true;
										addMessage(MessageHandler.PURCHASE_FAIL_OLDER_HD);
									}
								}else if(HF.getType()==HF.MEMORY){
									int type=new Integer((String)HF.getContent().get("data"));
									if((MEMORY_CHART[type]>MEMORY_CHART[memorytype])||(WATCH_CHART[type]>WATCH_CHART[memorytype])){
										memorytype=type;
										addMessage(MessageHandler.PURCHASE_NEW_MEMORY,new Object[]{HF.getName()});
									}else{
										buyFail=true;
										addMessage(MessageHandler.PURCHASE_FAIL_OLDER_MEMORY);
									}
								}else{
									HF.setLocation("");
									if(HF.getType()==HF.FIREWALL){
										HashMap SetLevel=HF.getContent();
										SetLevel.put("level","0");
									}
									MyComputerHandler.addData(new ApplicationData("savefile",O,0,ip),ip);
									NumberFormat format = NumberFormat.getCurrencyInstance();
									addMessage(MessageHandler.PURCHASE_SUCCESS,new Object[]{quantity,HF.getName(),format.format(price)});
								}
							
								if(price>0&&!buyFail){
									MyComputerHandler.addData(new ApplicationData("pettycash",new Object[]{new Float(price*-1.0f),false},0,ip),ip);
									MyComputerHandler.addData(new ApplicationData("pettycash",new Object[]{new Float(price),false},0,ip),payTarget);
								}
								MyComputerHandler.addData(new ApplicationData("requestwebpage",null,0,ip),MyApplicationData.getSourceIP());
								MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
							}
							systemChange=true;
						}else
						
						//ANOTHER PLAYER HAS REQUESTED YOUR WEB-PAGE.
						if(function.equals("requestwebpage")){
						
							Port TempPort=(Port)Ports.get(new Integer(defaultHTTP));
							if(TempPort!=null&&TempPort.getType()==Port.HTTP){
									if(TempPort.getProgram()!=null&&TempPort.getOn()){
										Object O = null;
										if(MyApplicationData.getParameters()!=null){
											O=((HashMap)MyApplicationData.getParameters()).get("Attack");
										}
										if(O!=null||type!=NPC){
										//	System.out.println(MyApplicationData.getParameters());//QUEST INFO.
											TempPort.getProgram().execute(MyApplicationData);
										}else{
											MyComputerHandler.addData(new ApplicationData("questinformation",new Object[]{MyApplicationData.getParameters(),InvolvedQuests},0,ip),MyApplicationData.getSourceIP());
										}
									}else{
										HTTPProgram HP=new HTTPProgram(this,MyComputerHandler);
										HP.serveWebPage(MyApplicationData,(Integer)((HashMap)MyApplicationData.getParameters()).get("packetid"));
									}
							}else{
								HTTPProgram HP=new HTTPProgram(this,MyComputerHandler);
								HP.serveWebPage(MyApplicationData,(Integer)((HashMap)MyApplicationData.getParameters()).get("packetid"));
							}
							systemChange=true;
						}else
						
						//INSERT QUEST SPECIFIC INFORMATION INTO AN HTTP REQUEST.
						if(function.equals("questinformation")){

							HashMap GetParameters=(HashMap)((Object[])MyApplicationData.getParameters())[0];
							ArrayList InterestedQuests=(ArrayList)((Object[])MyApplicationData.getParameters())[1];
							ArrayList QuestItems=MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
							if(QuestItems!=null)
							for(int ii=0;ii<QuestItems.size();ii++){
								HackerFile HF=(HackerFile)QuestItems.get(ii);
								HashMap Content=HF.getContent();
								int qFileQuantity=HF.getQuantity();
	
								String ItemName=(String)Content.get("itemname");
								GetParameters.put(ItemName,"true");
								GetParameters.put(ItemName+"_quantity",qFileQuantity+"");
								}

							for(int i=0;i<InterestedQuests.size();i++){//Only fetch tasks from quests we're interested in.
								HashMap Tasks=null;
								if(CurrentQuests.get(InterestedQuests.get(i))!=null){
									Tasks=(HashMap)((Object[])CurrentQuests.get(InterestedQuests.get(i)))[0];
								}
								
								if(Tasks!=null){
									Iterator TaskIterator=Tasks.entrySet().iterator();
									while(TaskIterator.hasNext()){
										Map.Entry CurrentEntry=(Map.Entry)TaskIterator.next();
										GetParameters.put(CurrentEntry.getKey(),""+((Object[])CurrentEntry.getValue())[0]);
									}
								}
							}
							
							//Add the completed quests to the parameters.
							for(int i=0;i<CompletedQuests.size();i++){
								GetParameters.put("quest"+((Object[])CompletedQuests.get(i))[0],"true");
							}
			
							//Add in started quests with the value false.
							Iterator Iterator1=CurrentQuests.entrySet().iterator();
							while(Iterator1.hasNext()){
								Map.Entry CurrentEntry=(Map.Entry)Iterator1.next();
								Integer questID=(Integer)CurrentEntry.getKey();
								GetParameters.put("quest"+questID,"false");
							}
							
							//Add commodity information to the quest parameters.
							for(int i=0;i<commodityAmount.length;i++){
								GetParameters.put("commodity"+i,""+commodityAmount[i]);
							}
														
							
							//Add in the different stats.
							GetParameters.put("Attack",""+getAttackLevel());
							GetParameters.put("Bank",""+getBankLevel());
							GetParameters.put("Watch",""+getWatchLevel());
							GetParameters.put("Scanning",""+getScanningLevel());
							GetParameters.put("FireWall",""+getFireWallLevel());
							GetParameters.put("HTTP",""+getHTTPLevel());
							GetParameters.put("pettycash",""+getPettyCash());
							GetParameters.put("Redirecting",""+getRedirectingLevel());
							GetParameters.put("Repair",""+getRepairLevel());
							
							//Add the player's default ports.
							GetParameters.put("defaultattack",""+getDefaultAttack());
							GetParameters.put("defaultbank",""+getDefaultBank());
							GetParameters.put("defaulthttp",""+getDefaultHTTP());
							GetParameters.put("defaultredirecting",""+getDefaultShipping());
							GetParameters.put("repaired",""+getRepaired());
							
							// Add in the player's current network.
							GetParameters.put(getNetwork(),"true");

							//Add in whether or not the player has made their first website.
							if(pageBody.length()>1){
								GetParameters.put("websitemade","true");
							}else{
								GetParameters.put("websitemade","false");
							}
							
							//Add information about whether or not a fire wall has been installed.
							if(checkFirewall()){
								GetParameters.put("firewallinstalled","true");
							}else{
								GetParameters.put("firewallinstalled","false");
							}
							
							//Add in a parameter that keeps track of whether you have a watch installed.
							if(MyWatchHandler.getWatchCount()>0){
								GetParameters.put("watchinstalled","true");
							}else{
								GetParameters.put("watchinstalled","false");
							}
													
							MyComputerHandler.addData(new ApplicationData("requestwebpage",GetParameters,0,ip),MyApplicationData.getSourceIP());
						}else
						
						//A PLAYER HAS BEEN REWARDED EXPERIENCE BY COMPLETING A QUEST TASK.
						if(function.equals("giveexperience")){
							String stat=((String)((Object[])MyApplicationData.getParameters())[0]).toLowerCase();
							float xp=(Float)((Object[])MyApplicationData.getParameters())[1];
							float amount=0.0f;
							
							float mult = 1.0f;
							xp *= mult;
							
							if(stat.equals("bank")){
								amount=(Float)Stats.get("Bank");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Bank",amount);
							}else
							
							if(stat.equals("attack")){
								amount=(Float)Stats.get("Attack");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Attack",amount);
							}else
							
							if(stat.equals("scanning")){
								amount=(Float)Stats.get("Scanning");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Scanning",amount);
							}else
							
							if(stat.equals("watch")){
								amount=(Float)Stats.get("Watch");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Watch",amount);
							}else
							
							if(stat.equals("firewall")){
								amount=(Float)Stats.get("FireWall");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("FireWall",amount);
							}else
							
							if(stat.equals("http")){
								amount=(Float)Stats.get("Webdesign");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Webdesign",amount);
							}else
							
							if(stat.equals("redirecting")){
								amount=(Float)Stats.get("Redirecting");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Redirecting",amount);
							}

							if(stat.equals("repair")){
								amount=(Float)Stats.get("Repair");
								amount+=xp;
								if(mult < 0) {
									if(amount <= 300.0f) amount = 300.0f;
								}
								Stats.put("Repair",amount);
							}
			
							healthChange=true;
						}else
																		
						//ANOTHER PLAYER HAS SUBMITTED A FORM TO A WEBSITE.
						if(function.equals("submit")){
							Port TempPort=(Port)Ports.get(new Integer(defaultHTTP));
							if(TempPort!=null&&TempPort.getType()==Port.HTTP){
									if(TempPort.getProgram()!=null&&TempPort.getOn()){
										TempPort.getProgram().execute(MyApplicationData);
									}
							}
							systemChange=true;
						}else

						//ANOTHER PLAYER HAS EXITED YOUR WEBSITE.
						if(function.equals("exit")){
							Port TempPort=(Port)Ports.get(new Integer(defaultHTTP));
							if(TempPort!=null&&TempPort.getType()==Port.HTTP){
									if(TempPort.getProgram()!=null){
										TempPort.getProgram().execute(MyApplicationData);
									}
							}
							systemChange=true;
						}else
						
						if(function.equals("vote")){
							if(getTotalLevel()<noobLevel){
								addMessage(MessageHandler.VOTE_FAIL_NOOB_LEVEL,new Object[]{noobLevel});
							}if(MyApplicationData.getSourceIP().equals(ip)){
								addMessage(MessageHandler.VOTE_FAIL_OWN_SITE);
							}else if(myVotes>0){
								MyMakeBounty.checkBounty(this,null,MakeBounty.VOTE,MyApplicationData.getSourceIP(),false,"");
							
								myVotes-=1;
								MyComputerHandler.addData(new ApplicationData("httpxp",new Float(500.7337f),0,ip),MyApplicationData.getSourceIP());
								addMessage(MessageHandler.VOTE_SUCCESS,new Object[]{myVotes});
							}else{
								addMessage(MessageHandler.VOTE_FAIL_NO_VOTES);
							}
							systemChange=true;
						}else
						
						//A REQUESTED WEB-PAGE HAS BEEN RETURNED.
						if(function.equals("webpage")){
							String PageTitle=(String)((Object[])MyApplicationData.getParameters())[0];
							String PageBody=(String)((Object[])MyApplicationData.getParameters())[1];
							Object Files[]=(Object[])((Object[])MyApplicationData.getParameters())[2];
							Object Temp[]=null;
							if(Files!=null)
								Temp=new Object[Files.length+1];
							else
								Temp=new Object[1];
							Temp[0]=(Integer)((Object[])MyApplicationData.getParameters())[3];
							
							if(Files!=null)
							for(int i=0;i<Files.length;i++)
								Temp[i+1]=Files[i];
								
							Files=Temp;
							
							PA.setBody(PageBody);
							PA.setTitle(PageTitle);
							PA.setDirectory(Files);
							systemChange=true;
						}else
						
						//SAVE THE SOURCE OF A PLAYER'S WEB PAGE.
						if(function.equals("savepage")){
							if(((String)((Object[])MyApplicationData.getParameters())[1]).length()>30000){
								addMessage(MessageHandler.WEBSITE_SAVE_FAIL_TOO_BIG);
							}else{
								pageTitle=(String)((Object[])MyApplicationData.getParameters())[0];
								pageBody=(String)((Object[])MyApplicationData.getParameters())[1];
								pageChanged=true;
							}
							systemChange=true;
						}else
						
						//SET THE QUANTITY ASSOCIATED WITH THE WATCH.
						if(function.equals("setwatchquantity")){
							int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							float quantity=(Float)((Object[])MyApplicationData.getParameters())[1];
							Watch twatch=(Watch)MyWatchHandler.getWatches().get(watchID);
							if(twatch!=null){
								twatch.setQuantity(quantity);
							}
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//SeT WHETHER THE GIVEN WATCH IS ON OR OFF.
						if(function.equals("setwatchonoff")){
							int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							boolean state=(Boolean)((Object[])MyApplicationData.getParameters())[1];
							Watch twatch=(Watch)MyWatchHandler.getWatches().get(watchID);
							if(twatch!=null){
								float cpuCheck=getCPULoad()+twatch.getActualCPUCost();
								float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
								if(state){
									if(MyWatchHandler.getWatchCount()<WATCH_CHART[memorytype]+MyEquipmentSheet.getWatchBonus()){
										if(cpuCheck<=maxCPU){
											twatch.setOn(state);
										}
									}else{
										addMessage(MessageHandler.WATCH_ON_FAIL);
									}
								}else{
									if(getCPULoad()<=maxCPU)
										twatch.setOn(state);
								}
							}
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//SET THE FIRE WALL THAT SHOULD BE SEARCHED FOR BY THE WATCH.
						if(function.equals("setwatchsearchfirewall")){
							int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							Integer searchFireWall=(Integer)((Object[])MyApplicationData.getParameters())[1];
							Watch twatch=(Watch)MyWatchHandler.getWatches().get(watchID);
							if(twatch!=null){
								twatch.setSearchFireWall(searchFireWall);
							}
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//SET THE NOTE ASSOCIATED WITH THIS WATCH.
						if(function.equals("setwatchnote")){
							int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							String note=(String)((Object[])MyApplicationData.getParameters())[1];
							Watch twatch=(Watch)MyWatchHandler.getWatches().get(watchID);
							if(twatch!=null){
								twatch.setNote(note);
							}
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//DELETE A WATCH FROM THE WATCH HANDLER.
						if(function.equals("deletewatch")){
							float maxCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus();
							if(getCPULoad()<=maxCPU){
								int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
								MyWatchHandler.removeWatch(watchID);
								MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
							}
						}else
						
						if(function.equals("checkbounty")){
							String fname=(String)MyApplicationData.getParameters();
							HackerFile HF=MyFileSystem.getFile("Store/",fname);
							if(HF!=null){
								HashMap Content=HF.getContent();
								float reward=new Float((String)Content.get("reward"));
								MyComputerHandler.addData(new ApplicationData("pettycash",new Float(reward),0,ip),MyApplicationData.getSourceIP());
								MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.BOUNTY_COMPLETED,new Object[]{NumberFormat.getCurrencyInstance().format(reward)}},0,ip),MyApplicationData.getSourceIP());
							}else
								MyComputerHandler.addData(new ApplicationData("message",MessageHandler.BOUNTY_FAILED_ALREADY_COMPLETED,0,ip),MyApplicationData.getSourceIP());

						}else
						
						//SET THE PORTS BEING OBSERVED BY THIS WATCH.
						if(function.equals("setwatchobservedports")){
							int watchID=(Integer)((Object[])MyApplicationData.getParameters())[0];
							Integer ObservedPorts[]=(Integer[])((Object[])MyApplicationData.getParameters())[1];
							Watch twatch=(Watch)MyWatchHandler.getWatches().get(watchID);
							if(twatch!=null){
								twatch.setObservedPorts(ObservedPorts);
							}
							MyComputerHandler.addData(new ApplicationData("fetchwatches",null,0,ip),ip);
						}else
						
						//A PLAYER IS REQUESTING A NETWORK HOP.
						if(function.equals("changenetwork")){
							String changeNetwork=(String)MyApplicationData.getParameters();
							
							
							if (network.equals(changeNetwork)) {
								addMessage(MessageHandler.CHANGE_NETWORK_FAIL_ALREADY_ON,new Object[]{network});
							} else if (network.equals(Network.JAIL_NETWORK)) {
								addMessage(MessageHandler.CHANGE_NETWORK_FAIL_JAILED,new Object[]{network});
							} else

							if (MyTime.getCurrentTime()-lastChangeNetwork<CHANGE_NETWORKS){
								addMessage(MessageHandler.CHANGE_NETWORK_FAIL_TIMEOUT, new Object[]{network});
							}
							
						 	else {
								boolean allowed=false;
                                // always allow a person onto the UGOPNet
								if (changeNetwork.equals("UGOPNet")) {
									allowed = true;
								} else {
									for(int i=0;i<AllowedNetworks.size();i++){
										String check=(String)AllowedNetworks.get(i);
										if(check.equals(changeNetwork)){
											allowed=true;
											break;
										}
									}
								}
                                // need to allow everybody that's already completed dedrick's quest (before networks existed) onto ProgNet
                                /*if (!allowed) {
                                    boolean dedrickDone = checkQuest(DEDRICKS_QUEST);
                                    if (dedrickDone) {
                                        AllowedNetworks.add("ProgNet");
                                    }
                                }*/ // we now have a working gateway that checks for this.
                                
								if(allowed){
									lastChangeNetwork = MyTime.getCurrentTime();
									
									Network.getInstance(MyComputerHandler).removeFromNetwork(network,ip);
									Network.getInstance(MyComputerHandler).addToNetwork(changeNetwork,ip);
									PacketNetwork PN=Network.getInstance(MyComputerHandler).getNetworkInformation(changeNetwork);
									store=PN.getStoreIP();
									PA.setPacketNetwork(PN);
									network=changeNetwork;
									addMessage(MessageHandler.CHANGE_NETWORK_SUCCESS,new Object[]{changeNetwork});
								}else{
									addMessage(Network.getInstance(MyComputerHandler).switchNetwork(network,changeNetwork,ip));
								}
							}
							systemChange=true;//Force a packet to sent.
						}else
						
						//A PLAYER IS REQUESTING A NETWORK HOP.
						if(function.equals("changenetwork2")){
							String changeNetwork=(String)MyApplicationData.getParameters();
							if (network.equals(changeNetwork)) {
								addMessage(MessageHandler.CHANGE_NETWORK_FAIL_ALREADY_ON,new Object[]{network});
							} else {
								/* -- edited version, don't need to be 'allowed'
								boolean allowed=false;
                                // always allow a person onto the UGOPNet
								if (changeNetwork.equals("UGOPNet")) {
									allowed = true;
								} else {
									for(int i=0;i<AllowedNetworks.size();i++){
										String check=(String)AllowedNetworks.get(i);
										if(check.equals(changeNetwork)){
											allowed=true;
											break;
										}
									}
								}
								*/
                                
                                
								if(true){
									lastChangeNetwork = MyTime.getCurrentTime();
									
									Network.getInstance(MyComputerHandler).removeFromNetwork(network,ip);
									Network.getInstance(MyComputerHandler).addToNetwork(changeNetwork,ip);
									PacketNetwork PN=Network.getInstance(MyComputerHandler).getNetworkInformation(changeNetwork);
									store=PN.getStoreIP();
									PA.setPacketNetwork(PN);
									network=changeNetwork;
									addMessage(MessageHandler.CHANGE_NETWORK_SUCCESS,new Object[]{changeNetwork});
								}
							}
							systemChange=true;//Force a packet to sent.
						}else
						
						//Create a new bounty.
						if(function.equals("makebounty")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							//new Object[]{anonymous,target,type,fname,folder,iterations,reward};
							boolean anonymous=(Boolean)O[0];
							String target=(String)O[1];
							int type=(Integer)O[2];
							String fileName=(String)O[3];
							String filePath=(String)O[4];
							int iterations=(Integer)O[5];
							float reward=(Float)O[6];
							
							if(!checkBank()){
								addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
							}else if(getPettyCash()>=reward){
								HashMap Content=new HashMap();
								Content.put("count",""+iterations);
								Content.put("type",""+type);
								Content.put("reward",""+reward);
								Content.put("target",""+target);
								Content.put("bountyip",""+ip);
								Content.put("timeout",""+getCurrentTime());
							
								HackerFile HF=new HackerFile(HackerFile.BOUNTY);
								HF.setQuantity(1);
								if(anonymous&&type!=MyMakeBounty.CHANGE){
									HF.setName(MakeBounty.getTypeName(type)+" By (Anonymous)");
								}else{
									HF.setName(MakeBounty.getTypeName(type)+" By ("+ip+")");
								}
								
								String Description="";
								Description+="Bounty Type: "+MakeBounty.getTypeName(type)+"\n";
								if(!target.equals("*"))
									Description+="Target: "+target+"\n";
								else
									Description+="Target: Any Player.\n";
								Description+="Reward: "+NumberFormat.getCurrencyInstance().format(reward)+"\n";

								
								if(type==MyMakeBounty.INSTALL&&fileName!=null&&!fileName.equals("")){
									HackerFile CheckFile=MyFileSystem.getFile(filePath,fileName);
									Description+="Must Install: "+CheckFile.getName()+" Maker: "+CheckFile.getMaker()+"\n";
									Content.put("maker",CheckFile.getMaker());
									Content.put("script",CheckFile.getName());
								}else{
									Content.put("maker","");
									Content.put("script","");
								}
								
								HF.setDescription(Description);
								HF.setQuantity(-1);
								HF.setContent(Content);
								HF.setLocation("Store/");
								Object O2[]=new Object[]{"Store/",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",O2,0,ip),store);
								MyComputerHandler.addData(new ApplicationData("pettycash",new Float(-1.0f*reward),0,ip),ip);
							}else{
								addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
							}
							systemChange=true;
						}else
						
						//COMPARE SCAN AND FIRE-WALL LEVEL AND RETURN THE FINAL SCAN INFORMATION TO THE PLAYER WHOM REQUESTED SCAN.
						if(function.equals("scan")){
							float scanXP=(Float)Stats.get("Scanning");
							int scanLevel=getLevel(scanXP);
							float xp=60.0f;
							
							if(getCPULoad()>getMaximumCPULoad()){
								addMessage(MessageHandler.SCAN_FAIL_OVERHEATED);
							}else if(!checkBank()){
								addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
							}else if(getPettyCash()<10.0){
								addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
							}else if(getPettyCash()>=10.0){
								int opponentFireWall=(Integer)((Object[])MyApplicationData.getParameters())[0];
								PacketPort PacketPorts[]=(PacketPort[])((Object[])MyApplicationData.getParameters())[1];
								//int oDefaultBank=(Integer)((Object[])MyApplicationData.getParameters())[2];
								//int oDefaultAttack=(Integer)((Object[])MyApplicationData.getParameters())[3];
								//int oDefaultFTP=(Integer)((Object[])MyApplicationData.getParameters())[4];
								//int oDefaultHTTP=(Integer)((Object[])MyApplicationData.getParameters())[5];
								boolean npc=(Boolean)((Object[])MyApplicationData.getParameters())[6];
								//int oDefaultShipping=(Integer)((Object[])MyApplicationData.getParameters())[7];

								if(!isNPC()){
									MyMakeBounty.checkBounty(this,null,MyMakeBounty.SCAN,MyApplicationData.getSourceIP(),false,"");
								}
								
								for(int i=0;i<PacketPorts.length;i++){
									PacketPort P=(PacketPort)PacketPorts[i];
									if(P!=null){
										if(scanLevel-opponentFireWall<25){
											xp=40.0f;
											P.setDefault(-1);
										}else{
											P.setDefault(0);
											P.setNote(MyApplicationData.getSourceIP());
											
											if(P.getParamIndex() > 0)
											{
												if(P.getNumber() == (Integer)((Object[])MyApplicationData.getParameters())[P.getParamIndex()])
													P.setDefault(1);
											}
											/*
											if(P.getType()==PacketPort.BANKING&&P.getNumber()==oDefaultBank)
												P.setDefault(1);
											if(P.getType()==PacketPort.ATTACK&&P.getNumber()==oDefaultAttack)
												P.setDefault(1);
											if(P.getType()==PacketPort.SHIPPING&&P.getNumber()==oDefaultShipping)
												P.setDefault(1);
											if(P.getType()==PacketPort.FTP&&P.getNumber()==oDefaultFTP)
												P.setDefault(1);
											if(P.getType()==PacketPort.HTTP&&P.getNumber()==oDefaultHTTP)
												P.setDefault(1);
											*/
										}
										
										if(scanLevel-opponentFireWall<15){
											xp=20.0f;
											P.setFireWall(null);
										}
									
									}
									PA.setScannedPorts(PacketPorts);
								}

								MyComputerHandler.addData(new ApplicationData("pettycash",new Float(-10.0f),0,ip),ip);
								MyComputerHandler.addData(new ApplicationData("scanxp",new Float(xp),0,ip),ip);
								MyComputerHandler.addData(new ApplicationData("scansuccess",null,0,ip),MyApplicationData.getSourceIP());
							//	MyMakeClue.checkClue(this,MyApplicationData.getSourceIP(),MakeClue.SCAN);//Check the clue status.
							}else{
								addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
							}
							systemChange=true;
						}else if(function.equals("scansuccess")){
						//To hell with them.
						}else
												
						//REQUEST A SCAN OF AN OPPONENT.
						if(function.equals("requestscan")){
							float fireWallXP=(Float)Stats.get("FireWall");
							int fireWallLevel=getLevel(fireWallXP);
							
							String target=(String)(MyApplicationData.getParameters());
							PacketPort PacketPorts[]=new PacketPort[Ports.size()];
							
							Iterator PortIterator=Ports.entrySet().iterator();
							int ii=0;
							while(PortIterator.hasNext()){
								Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
								if(TempPort.getOn())
									PacketPorts[ii]=TempPort.getPacketPort(); 
								ii++;
							}
							Object O[]=new Object[]{new Integer(fireWallLevel),PacketPorts,new Integer(defaultBank),new Integer(defaultAttack),new Integer(defaultFTP),new Integer(defaultHTTP),new Boolean(type==NPC),new Integer(defaultShipping)};
							MyComputerHandler.addData(new ApplicationData("scan",O,0,ip),target);
						}else
						
						//THIS IS WHAT HAPPENS WHEN A PLAYER IS GIVEN A NEW QUEST TASK FROM AN NPC.
						if(function.equals("givetask")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							String TaskName=(String)O[0];
							String TaskLabel=(String)O[1];
							Integer QuestID=(Integer)O[2];
							
							if(!checkQuest(QuestID)){//Make sure the quest isn't already complete.
								HashMap CurrentQuest=null;
								String label="";
								if(CurrentQuests.get(QuestID)!=null){
									CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
									label=(String)((Object[])CurrentQuests.get(QuestID))[1];
								}
																
								if(CurrentQuest==null){
									CurrentQuest=new HashMap();
									CurrentQuest.put(TaskName,new Object[]{new Boolean(false),TaskLabel});
									CurrentQuests.put(QuestID,new Object[]{CurrentQuest,label});
								}else{
									CurrentQuest.put(TaskName,new Object[]{new Boolean(false),TaskLabel});
								}
							}
							
						}else
						
						//THIS SETS THE VALUE OF AN EXISTING TASK, IN CASE WE WANT TO SET SOMEONE BACK IN A QUEST. (Can also use it to give a new one)
						if(function.equals("settask")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							String TaskName=(String)O[0];
							Integer QuestID=(Integer)O[1];
							Boolean SetTo=(Boolean)O[2];
							
							if(!checkQuest(QuestID)){//Make sure the quest hasn't already been completed.
								HashMap CurrentQuest=null;
								String label="";
								if(CurrentQuests.get(QuestID)!=null){
                                    // they have this quest already; get the quest and the name of the quest
									CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
									label=(String)((Object[])CurrentQuests.get(QuestID))[1];
								}

								if(CurrentQuest==null){
                                    // they didn't have this quest already; create the quest with "", and create the task
									CurrentQuest=new HashMap();
									O=(Object[])CurrentQuests.get(TaskName);
									CurrentQuest.put(TaskName,new Object[]{SetTo,O[1]});
									CurrentQuests.put(QuestID,new Object[]{CurrentQuest,label});
								} else {
                                    // the quest already exists; get the task and set it's value
                                    // wouldn't this throw an exception if you cast (Object[])null ?  I want to set a task that doesn't exist yet.
                                    if (CurrentQuest.get(TaskName) == null) {
                                        // since you don't give a task description when you use setTask, if the task is a new task, we'll use the taskname as the description
                                        CurrentQuest.put(TaskName, new Object[]{SetTo, TaskName});
                                    } else {
                                        O=(Object[])CurrentQuest.get(TaskName);
                                        CurrentQuest.put(TaskName,new Object[]{SetTo,O[1]});
                                    }
								}
							}
							
							HashMap TempHashMap=new HashMap();
							TempHashMap.put("packetid",-1);
							MyComputerHandler.addData(new ApplicationData("requestwebpage",TempHashMap,0,ip),MyApplicationData.getSourceIP());
						}else
						
						//THIS SETS THE VALUE OF AN EXISTING TASK, IN CASE WE WANT TO SET SOMEONE BACK IN A QUEST. (Can also use it to give a new one)
						if(function.equals("completetask")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							String TaskName=(String)O[0];
							Integer QuestID=(Integer)O[1];
							Boolean SetTo=(Boolean)true;
							
							if(!checkQuest(QuestID)){//Make sure the quest hasn't already been completed.
								HashMap CurrentQuest=null;
								String label="";
								if(CurrentQuests.get(QuestID)!=null){
                                    // they have this quest already; get the quest and the name of the quest
									CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
									label=(String)((Object[])CurrentQuests.get(QuestID))[1];
								}

								if(CurrentQuest==null){
                                    // they didn't have this quest already; create the quest with "", and create the task
									CurrentQuest=new HashMap();
									O=(Object[])CurrentQuests.get(TaskName);
									CurrentQuest.put(TaskName,new Object[]{SetTo,O[1]});
									CurrentQuests.put(QuestID,new Object[]{CurrentQuest,label});
								} else {
                                    // the quest already exists; get the task and set it's value
                                    // wouldn't this throw an exception if you cast (Object[])null ?  I want to set a task that doesn't exist yet.
                                    if (CurrentQuest.get(TaskName) == null) {
                                        // since you don't give a task description when you use setTask, if the task is a new task, we'll use the taskname as the description
                                        CurrentQuest.put(TaskName, new Object[]{SetTo, TaskName});
                                    } else {
                                        O=(Object[])CurrentQuest.get(TaskName);
                                        CurrentQuest.put(TaskName,new Object[]{SetTo,O[1]});
                                    }
								}
							}
						}else
						
						//THIS IS WHAT HAPPENS WHEN A PLAYER IS GIVEN A NEW QUEST TASK FROM AN NPC.
						if(function.equals("givecommodity")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							int commodityType=(Integer)O[0];
							float amount=(Float)O[1];
							setCommodityAmount(commodityType,getCommodity(commodityType)+amount);
						}else
						
						//Gives a player access to a specific network in their allowed networks array.
						if(function.equals("giveaccess")){
							String accessNetwork=(String)MyApplicationData.getParameters();
							AllowedNetworks.add(accessNetwork);
						}else
						
						//Give a quest specic item to a player.
						if(function.equals("givefile")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							
							String fileID=(String)O[0];
							int quan = (int)((Integer)O[1]);

							if(MyDropTable==null)
								MyDropTable=new DropTable(dropTable,this);
							
							HackerFile HF=MyDropTable.getQuestItem(fileID);
														
							if(HF!=null){
								HF.setQuantity(quan);
								Object Parameter[]=new Object[]{"",HF};
								MyComputerHandler.addData(new ApplicationData("savefile",Parameter,0,ip),MyApplicationData.getSourceIP());
								MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.GIVEN_FILE,new Object[]{HF.getName(),quan}},0,ip),MyApplicationData.getSourceIP());
							}
							
						}else
						
						//Take a quest specific item from a player.
						if(function.equals("takefile")){
							String fileID=(String)MyApplicationData.getParameters();
							//ArrayList Files=MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
							ArrayList Files=MyFileSystem.getFiles();
							if(Files!=null)
							for(int i=0;i<Files.size();i++){
								HashMap Content=((HackerFile)Files.get(i)).getContent();
								String itemname=(String)Content.get("itemname");
								if(itemname.equals(fileID)){
									Object O[]=new Object[]{"",((HackerFile)Files.get(i)).getName()};
									MyComputerHandler.addData(new ApplicationData("deletefile",O,0,ip),ip);
								}
							}
						}else
						
						//Take an item (of the given quantity) from a player.
						if(function.equals("takefile2")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							String fileID=(String)O[0];
							int quan = (int)((Integer)O[1]);
							ArrayList Files=MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
							if(Files!=null)
							for(int i=0;i<Files.size();i++){
								HashMap Content=((HackerFile)Files.get(i)).getContent();
								String itemname=(String)Content.get("itemname");
								if(itemname.equals(fileID)){
									HackerFile HF = ((HackerFile)Files.get(i));
									int currentQuantity = HF.getQuantity();
									int newQuantity = currentQuantity - quan;
									if(newQuantity > 0) {
										HackerFile HFCheck=MyFileSystem.getFile("",HF.getName());
										/* bug fix for the takeFile bug giving -quantities
										 * it was caused by the object references for HF and HFCheck being the same (dunno how that worked)
										 * so here's the hack fix
										**/
										// these two lines commented out, that was the old way
										//if(HFCheck.getQuantity()==0) { HFCheck.setQuantity(currentQuantity); }
										//HF.setQuantity(-1 * quan);
										
										// here I use a function I wrote to bypass any file quantity calculation, and just set it to the right value
										saveFileTemp(HF,HFCheck,"",newQuantity);
										// instead of
										//saveFile(HF,HFCheck,"");
										
										MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.FILE_TAKEN,new Object[]{HF.getName(),quan}},0,ip),ip);
									} else if(newQuantity == 0) {
										Object O1[]=new Object[]{"",((HackerFile)Files.get(i)).getName()};
										MyComputerHandler.addData(new ApplicationData("deletefile",O1,0,ip),ip);
									}
								}
							}
						}else
																							
						//FINISHES A SPECIFIC QUEST.
						if(function.equals("finishquest")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							Integer QuestID=(Integer)O[0];
							O=(Object[])CurrentQuests.remove(QuestID);
							if(O!=null){
								CompletedQuests.add(new Object[]{QuestID,O[1]});
								addMessage(MessageHandler.QUEST_COMPLETED,new Object[]{O[1]});
							}
						}else
																							
						//FINISHES A SPECIFIC QUEST.
						if(function.equals("givequest")){
							Object O[]=(Object[])MyApplicationData.getParameters();
							Integer QuestID=(Integer)O[0];
							String description=(String)O[1];
							CurrentQuests.put(QuestID,new Object[]{new HashMap(),description});
							addMessage(MessageHandler.QUEST_GIVEN,new Object[]{description});
						}else
						
						//TAKE MONEY IS USED TO REQUEST MONEY AS A PART OF A QUEST OBLIGATION.
						if(function.equals("takemoney")){
							float amount=(Float)((Object[])MyApplicationData.getParameters())[0];
							//String TaskName=(String)((Object[])MyApplicationData.getParameters())[1];
							//Integer QuestID=(Integer)((Object[])MyApplicationData.getParameters())[2];

							if(getPettyCash()>=amount){
								setPettyCash(getPettyCash()-amount);
								//HashMap CurrentQuest=null;
								//if(CurrentQuests.get(QuestID)!=null)
									//CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
								
								//if(CurrentQuest!=null)
									//CurrentQuest.put(TaskName,new Object[]{new Boolean(true),""});
							}
							/* removed autorefresh
							HashMap TempHashMap=new HashMap();
							TempHashMap.put("packetid",-1);
							MyComputerHandler.addData(new ApplicationData("requestwebpage",TempHashMap,0,ip),MyApplicationData.getSourceIP());
							*/
						}else
						
						//TAKES A COMMODITY AS PART OF A QUEST REQUIREMENT.
						if(function.equals("takecommodity")){
							float amount=(Float)((Object[])MyApplicationData.getParameters())[0];
							int CommodityType=(Integer)((Object[])MyApplicationData.getParameters())[1];
							//String TaskName=(String)((Object[])MyApplicationData.getParameters())[2];
							//Integer QuestID=(Integer)((Object[])MyApplicationData.getParameters())[3];

							if(getCommodity(CommodityType)>=amount){
								setCommodityAmount(CommodityType,getCommodity(CommodityType)-amount);
								
								//HashMap CurrentQuest=null;
								//if(CurrentQuests.get(QuestID)!=null)
									//CurrentQuest=(HashMap)((Object[])CurrentQuests.get(QuestID))[0];
								//if(CurrentQuest!=null)
									//CurrentQuest.put(TaskName,new Object[]{new Boolean(true),""});
							}
							/* removed the auto refresh
							HashMap TempHashMap=new HashMap();
							TempHashMap.put("packetid",-1);
							MyComputerHandler.addData(new ApplicationData("requestwebpage",TempHashMap,0,ip),MyApplicationData.getSourceIP());
							*/
						}else
	
						//THIS FUNCTION ALLOWS YOU TO EXCHANGE A COMMODITY FOR A FILE ON YOUR HD, MAKING IT SAFE.
						if(function.equals("exchangecommodity")){
							Integer ExchangeAmount=(Integer)((Object[])MyApplicationData.getParameters())[0];
							Integer CommodityType=(Integer)((Object[])MyApplicationData.getParameters())[1];
							float ExchangeCost=(Float)((Object[])MyApplicationData.getParameters())[2];
							
							boolean failed=false;
							
                            String name="DuctTape";
							if(checkBank()&&getPettyCash()>=ExchangeCost&&MyFileSystem.getSpaceLeft()>0){
								if(getCommodity(CommodityType)>=ExchangeAmount){// We have enough of the commodity and enough money.
									setPettyCash(getPettyCash()-ExchangeCost);
									setCommodityAmount(CommodityType,getCommodity(CommodityType)-ExchangeAmount);
									HackerFile NewHackerFile=new HackerFile(HackerFile.COMMODITY_SLIP);
									NewHackerFile.setDescription("This slip is can be exchanged for the given commodity.");
									NewHackerFile.setMaker("Ming");
									NewHackerFile.setQuantity(ExchangeAmount);
									HashMap attributes=new HashMap();
									attributes.put("data",""+CommodityType);
									attributes.put("level","0");
									NewHackerFile.setContent(attributes);
									
									if(CommodityType==0){
										name="DuctTape.commodity";
									}else if(CommodityType==1){
										name="Germanium.commodity";
									}else if(CommodityType==2){
										name="Silicon.commodity";
									}else if(CommodityType==3){
										name="YBCO.commodity";
									}else if(CommodityType==4){
										name="Plutonium.commodity";
									}
									NewHackerFile.setName(name);
									Object Parameter[]=new Object[]{"",NewHackerFile};
									MyComputerHandler.addData(new ApplicationData("savefile",Parameter,0,ip),ip);
								}else{
									failed=true;
									addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_COMMODITY,new Object[]{name});
								}
							}else if(!checkBank()){
								failed=true;
								addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT);
							}else if(getPettyCash()<=ExchangeCost){
								failed=true;
								addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
							}else if(MyFileSystem.getSpaceLeft()<=0){
								failed=true;
								addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_HD_FULL);
							}
				
							if(!failed)
								addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS,new Object[]{ExchangeAmount,name});


							systemChange=true;
						}else
						
						//THIS FUNCTION ALLOWS YOU TO CHANGE A FILE BACK INTO A COMMODITY.
						if(function.equals("exchangefile")){
							Integer ExchangeAmount=(Integer)((Object[])MyApplicationData.getParameters())[0];
							Integer CommodityType=(Integer)((Object[])MyApplicationData.getParameters())[1];
							float ExchangeCost=(Float)((Object[])MyApplicationData.getParameters())[2];
							
							boolean failed=false;
							
							HackerFile TempFile = null;
							if(checkBank() && getPettyCash() >= ExchangeCost){
								ArrayList CommodityFiles=MyFileSystem.getFilesOfType(HackerFile.COMMODITY_SLIP);
								int FileAmount=0;
								if(CommodityFiles!=null)
								
								
								for(int i=0;i<CommodityFiles.size();i++){
									TempFile=(HackerFile)CommodityFiles.get(i);
									int type=new Integer((String)TempFile.getContent().get("data"));
									if(type==CommodityType){
										if(TempFile.getQuantity()>=ExchangeAmount){
											setPettyCash(getPettyCash()-ExchangeCost);
											setCommodityAmount(CommodityType,getCommodity(CommodityType)+ExchangeAmount);
											TempFile.setQuantity(TempFile.getQuantity()-ExchangeAmount);
											if(TempFile.getQuantity()==0){//Delete the file if it's empty.
												String path="";
												String name=TempFile.getName();
												MyFileSystem.deleteFile(path,name);
												PA.setRequestPrimary(true,1);
											}
										}else
											failed=true;
										break;
									}
									
									if(i==CommodityFiles.size()-1)
										failed=true;
								}
								
							}else if(!checkBank()){
								failed=true;
								addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT);
							}else if(getPettyCash() <= ExchangeCost){
								failed=true;
								addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
							}
				
							if(!failed){
								addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS_FROM_FILE,new Object[]{ExchangeAmount,TempFile.getName()});
							}
							
							systemChange=true;
						}else
						
						//REQUEST THAT A PORT LISTING BE PROVIDED TO THE PACKET.
						if(function.equals("fetchports")){
							PacketPort PacketPorts[]=new PacketPort[Ports.size()];
							Iterator PortIterator=Ports.entrySet().iterator();
							int ii=0;
							while(PortIterator.hasNext()){
								Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
								PacketPorts[ii]=TempPort.getPacketPort();
								ii++;
							}
							PA.setPacketPorts(PacketPorts);
							systemChange=true;
						}else
						
						//PING.
						if(function.equals("ping")){
						//TO HELL WITH THEM.
						}
						
						//BY DEFAULT A REMOTE FUNCTION CALL IS DISPATCHED TO A PORT FOR PROCESSING.
						else{
							checkedWatch=dispatchToPortOrFail(MyApplicationData,port);
						}
						
						finalizeProcessedApplicationData(MyApplicationData,function,startTime,checkedWatch);
					}
				
	}

	private void updateApplicationActivity(ApplicationData applicationData,String function){
		if(function.equals("ping")){
			lastPingTime = MyTime.getCurrentTime();
			if(logInTime == 0){
				logInTime = MyTime.getCurrentTime();
			}
		}
		
		if(clientPackets.containsKey(function)){
			if(lastClientPacketTime == 0 || logInTime == 0){
				logInTime = MyTime.getCurrentTime();
			}
			lastClientPacketTime = MyTime.getCurrentTime();
			int lockCountAdd = (Integer)clientPackets.get(function);
			lockCount+=lockCountAdd;
		}

		if(applicationData.getSource()==ApplicationData.OUTSIDE)
			GUI_READY=true;
	}

	private int normalizeTransferPort(ApplicationData applicationData,String function,int port){
		if(function.equals("requestsecondarydirectory")||function.equals("put")||function.equals("get")||function.equals("finalizeput")){
			String targetIP=(String)((Object[])applicationData.getParameters())[0];

			if(!targetIP.equals(ip)){
				port=defaultFTP;
				Port P=(Port)Ports.get(new Integer(port));
				if(P!=null){
					if(P.getType()!=P.FTP){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_WAS_NOT_FTP,new Object[]{port,ip}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}else if(P.getDummy()){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_WAS_DUMMY,new Object[]{port,ip},new Object[]{applicationData.getSourcePort(),targetIP}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}else if(!P.getOn()){
						MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_NOT_ON,new Object[]{port,ip}},0,ip),targetIP);
						if(function.equals("finalizeput"))
							P.friendlyPut(applicationData);
					}

				}else{
					if(function.equals("finalizeput"))
						P.friendlyPut(applicationData);
					MyComputerHandler.addData(new ApplicationData("message",MessageHandler.FTP_NOT_FOUND,0,ip),targetIP);
				}
			}
		}
		return(port);
	}

	private void maybeLogIncomingMessage(ApplicationData applicationData,String function){
		if(function.equals("logmessage")){
			String message = (String)((Object[])applicationData.getParameters())[0];
			String ip = (String)((Object[])applicationData.getParameters())[1];
			Long timestamp = (Long)((Object[])applicationData.getParameters())[2];
			logMessage(message,ip,timestamp);
		}
	}

	private boolean dispatchToPortOrFail(ApplicationData applicationData,int port){
		if(Ports.get(new Integer(port))!=null){
			Port tempport=(Port)Ports.get(new Integer(port));
			tempport.setCurrentPacket(PA);
			tempport.addApplicationData(applicationData,MyTime.getCurrentTime());
			
			currentWatchCost=MyWatchHandler.checkWatches(applicationData,Ports,pettyCash);
			return(true);
		}else{
			if(applicationData.getFunction().equals("damage"))
				MyComputerHandler.addData(new ApplicationData("requestcancelattack",null,applicationData.getSourcePort(),this.getIP()),applicationData.getSourceIP());

			if(!applicationData.getSourceIP().equals(ip))
				MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.PORT_NOT_ON,new Object[]{port,ip}},0,ip),applicationData.getSourceIP());
			addMessage(MessageHandler.COULD_NOT_EXECUTE_APPLICATION,new Object[]{port});
			systemChange=true;
			return(false);
		}
	}

	private void finalizeProcessedApplicationData(ApplicationData applicationData,String function,long startTime,boolean checkedWatch){
		if(!function.equals("requestequipment")){
			lastAccessed=startTime;
		}
		if(!checkedWatch)
			currentWatchCost=MyWatchHandler.checkWatches(applicationData,Ports,pettyCash);
	}

	private void runLoopMaintenance(long startTime){
		//Check whether this player has purchased any file packs.
		if(iterationCount%150==0){
			GiveItemsSingleton.getInstance().giveFiles(this,RawComputerHandler);
		}
		
		//Check whether or not a packet should currently be sent.
		sendStandardPacket();
		            			
		//Run the saving logic.
		runSavingLogic();
							
		//check the ping times to see if we need to write statistics.
		checkPingTime();
		
		//Check the player's daily pay, and provide it if needed.
		checkDailyPay();

		//Runs the attack logic this includes calculating the current CPU costs.
		runAttackLogic();
		
		//Macro Protection.
		if(operationCount>6000&&!isNPC()){
			RawComputerHandler.broadcast(new ApplicationData("message",new Object[]{MessageHandler.PLAYER_BUSY,new Object[]{ip}},0,""));
			operationCount=0;
		}
		
		if(!isNPC()){
			if(getLoaded()&&!getLoading()&&lockCount>=CAPTCHA_COUNT&&(!locked||RESEND_CAPTCHA)){
				locked=true;
				Object O[]=Computer.generateImage();
				PA.setCAPTCHA(O[0]);
				unlockKey=(String)O[1];
				sendPacket();
				RESEND_CAPTCHA=false;
			}
		}
		
		//Sleep to cut down on processor load.
		if(Tasks.size()==0){
			try{
				long endTime=MyTime.getCurrentTime();
				if(SLEEP_TIME-(endTime-startTime)>0)
					MyThread.sleep(SLEEP_TIME-(endTime-startTime));
			}catch(InterruptedException e){
				if(run){
					e.printStackTrace();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}else{
			if(Tasks.size()>10){
				CentralLogging.getInstance().addOutput("Username: "+userName+" IP:"+ip+" Spamming?\n");
			}
		}
		
		//Dispatch a 3D chat update.
		if(getLoaded()&&!getLoading()&&type!=NPC&&GUI_READY)
			sendChatPacket();
		
		try{
			MyThread.sleep(50);
		}catch(InterruptedException e){
			if(run){
				e.printStackTrace();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	Run the logic used to perform the saving process of accounts.
	*/
	public void runSavingLogic(){
		//System.out.println("Running Saving Logic");
		//WRITE THE COMPUTER BACK TO DISK WHEN A TIMEOUT IS REACHED.
		//System.out.println("Computer Timeout: "+COMPUTER_TIMEOUT+" Logged Time:"+(MyTime.getCurrentTime()-lastAccessed));
		if((MyTime.getCurrentTime()-lastAccessed>COMPUTER_TIMEOUT||LOGOUT||LOAD_FAILURE||(countDown&&MyTime.getCurrentTime()-countDownStart>COUNTDOWN_LENGTH))&&Loaded){
			//Message the player who requested this load with the error message.
			if(!loadRequester.equals(ip)&&LOAD_FAILURE&&!loadRequester.equals("")){
				Iterator MyIterator=Tasks.iterator();
				Object o=null;
				if(MyIterator.hasNext()){
					o=MyIterator.next();
					if(o instanceof ApplicationData){
						ApplicationData AD=(ApplicationData)o;
						if(AD.getFunction().equals("pettycash")){
							MyComputerHandler.addData(AD,AD.getSourceIP());
						}
						
						if(AD.getFunction().equals("requestwebpage")){
							String PageTitle="Server Not Found";
							String PageBody="<html><head><title>Hack Wars - Error report</title><style><!--H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;color:white} H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} BODY {background-color:rgb(0,0,0);font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;color:white;} B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;color:white;} P {color:white;font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}A {color : black;}A.name {color : black;}HR {color : #525D76;}--></style> </head><body><h1 style=\"width:100%\">HTTP Status 408</h1><HR size=\"1\" noshade=\"noshade\"><p style=\"background-color:black;\"><b>type</b> HTTP Error</p><p style=\"background-color:black;\"><b>message</b> <u>Resource not found.</u></p><p style=\"background-color:black\"><b>description</b> <u>The HTTP server of the player you attempted to connect to does not seem to be on.</u></p><HR size=\"1\" noshade=\"noshade\"><h3>&copy; Hack Wars</h3></body></html>";
							Object[] Files=null;
							Object O[]=new Object[]{PageTitle,PageBody,Files,0};
							MyComputerHandler.addData(new ApplicationData("webpage",O,0,ip),AD.getSourceIP());
						}
					}
					MyIterator.remove();
				}
				
			
				MyComputerHandler.addData(new ApplicationData("message",errorMessage,0,ip),loadRequester);
			}

			if(!LOAD_FAILURE){//Only write to disk if the file didn't fail to load.
				try{
					MysqlHandler.addWork(new Object[]{ip,this,"asdbas0d98a0sd9fa8sasdlbo",new Boolean(pageChanged),pageTitle,pageBody});
				}catch(Exception e){
					e.printStackTrace();
				}

			}
							
			Loaded=false;
			RawComputerHandler.addData(null,ip);
			if(type!=NPC)
				RawComputerHandler.decrementPlayers();
		}else if(!LOAD_FAILURE){//Perform an auto-save every 10 minutes or so.
			if(lastSave==0)
				lastSave=MyTime.getCurrentTime();
			if(MyTime.getCurrentTime()-lastSave>AUTO_SAVE){
				MyEquipmentSheet.degradeEquipment();//This is a good time to check whether or not equipment has degraded.
							
				lastSave=MyTime.getCurrentTime();
				try{
					MysqlHandler.addWork(new Object[]{ip,this,"asdbas0d98a0sd9fa8sasdlbo",new Boolean(pageChanged),pageTitle,pageBody});
				}catch(Exception e){
					e.printStackTrace();
				}
				MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
			}
		}
	}
	
	/**
	Checks wheter or not we have stopped getting a ping and therefore need to write to a db how long the player has been playing.
	*/
	public void checkPingTime(){
		game.computer.session.PlayStatisticsResult result = sessionService.recordPlayStatistics(
			new PlayStatisticsRequest(
				loggedIn,
				ip,
				MyTime.getCurrentTime(),
				logInTime,
				lastPingTime,
				lastClientPacketTime
			)
		);
		lastPingTime = result.getLastPingTimeMillis();
		logInTime = result.getLogInTimeMillis();
		lastClientPacketTime = result.getLastClientPacketTimeMillis();
	}
		
	/**
	Checks whether or not the player should be given their daily pay and provides it.
	*/
	public void checkDailyPay(){
		/**
		CHECK WHETHER IT IS TIME FOR DAILY PAY AND PROVIDE IT TO THE AD REVENUE TARGET.
		*/
		if(lastPaid<=100)//Make sure that the first time the player plays they don't get paid.
			lastPaid=MyTime.getCurrentTime();

		if(MyTime.getCurrentTime()-lastPaid>PAY_PERIOD&&Loaded&&!inactive){
			Port TempPort=null;
			
			myVotes+=1;//Get some more votes.
			if(myVotes>4)
				myVotes=4;
			
			if(!checkHTTP()){
				logMessage("Did not receive income from website because HTTP is not installed.",ip,lastPaid+PAY_PERIOD);
				lastPaid = MyTime.getCurrentTime();
			}else{
				float mod=(getHTTPLevel()-1.0f)*50.0f;
				if(type==NPC)
					mod=0.0f;
					
				float amount=(dailyPaySize+mod)*0.75f*dailyPayReduction;
				float extra = (dailyPaySize+mod)*0.75f-amount;
				if(extra>0.0f){
					pettyCash+=extra;
					String message="Transferred "+NumberFormat.getCurrencyInstance().format(extra)+" of daily pay from "+ip+".";
					logMessage(message,ip,new Long(lastPaid+PAY_PERIOD));
				}
				
				ApplicationData MyPettyCash=new ApplicationData("pettycash",new Object[]{new Float(amount),false},0,ip);
				MyComputerHandler.addData(MyPettyCash,adRevenueTarget);
				ApplicationData MyHTTPXP = new ApplicationData("httpxp",new Float(getHTTPLevel()*10.0f),0,ip);
				MyComputerHandler.addData(MyHTTPXP,adRevenueTarget);
				String message="Transferred "+NumberFormat.getCurrencyInstance().format(amount)+" of daily pay from "+ip+".";
				Object[] logMessageParameters = new Object[]{message,ip,lastPaid+PAY_PERIOD};
				MyPettyCash=new ApplicationData("logmessage",logMessageParameters,0,ip);
				MyComputerHandler.addData(MyPettyCash,adRevenueTarget);
				message = "Received $"+(dailyPaySize+mod)*0.25+" in guaranteed income to bank.";
				logMessageParameters = new Object[]{message,ip,lastPaid+PAY_PERIOD};
				MyPettyCash=new ApplicationData("logmessage",logMessageParameters,0,ip);
				MyComputerHandler.addData(MyPettyCash,ip);
				bankMoney += (dailyPaySize+mod)*0.25;
				lastPaid+=PAY_PERIOD;
			}
			
		}
		else if(inactive){
			logMessage("Did not receive income because you were inactive.",ip,MyTime.getCurrentTime());
			lastPaid=MyTime.getCurrentTime();
			inactive = false;
		}
	}
	
	/**
	This function runs the attack logic, this takes care of dealing with overheating, applying hardware, and calculating
	current CPU costs.
	*/
	public void runAttackLogic(){
			/**
			CHECK FOR ATTACKS/PERFORM HEALING AT THE GIVEN RATE/CALCULATE CPU LOAD.
			Deals with: Attacking, Healing, Over Heating.
			*/
			if(getLoaded()&&!getLoading()&&MyTime.getCurrentTime()-lastAttack>ATTACK_RATE){			
				float startReportCPU=reportCPU;
				
				if(!cpuLoadCalculated)//Check the current watch cost.
					currentWatchCost=MyWatchHandler.checkWatches(new ApplicationData("null",null,0,ip),Ports,pettyCash);
				cpuLoadCalculated=true;
				
				float startCPU=currentCPU;//What was the CPU cost at the start?
			
				//Heal the port at a given rate -- at this time once every 6 seconds.
				boolean heal=false;
				boolean overHeated=false;
				if(healCounter%MyEquipmentSheet.getHealMod()==0)
					heal=true;
				if(currentCPU>CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()){
					overHeated=true;
					if(overheatStart==-1)
						overheatStart=MyTime.getCurrentTime();
				}else if(MyTime.getCurrentTime()-overheatStart>OVER_HEAT_TIME&&overheatStart!=-1){
					overheatStart=-1;
				}else if(overheatStart!=-1){
					overHeated=true;
				}
			
				Iterator PortIterator=Ports.entrySet().iterator();
				int ii=0;
				float tempCPULoad=0.0f;
				float tempBaseCPULoad=0.0f;
				while(PortIterator.hasNext()){
					Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
					
					//If heal true heal the port.
					if(heal){
						if(TempPort.damagePort(-1.0f)){
							healthChange=true;
							MyWatchHandler.updateInitialHealthQuanity(TempPort.getNumber(),TempPort.getHealth());
						}
					}
					//Put the ports into over-heat mode.
					if(overHeated){
						if(!TempPort.getOverHeated()&&TempPort.getHealth()!=100){
							if(TempPort.getType()==Port.ATTACK||TempPort.getType()==Port.SHIPPING){
								if(TempPort.getProgram() instanceof AttackProgram){
									AttackProgram TempProgram=(AttackProgram)TempPort.getProgram();
									if(TempProgram.isZombie())
										MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.ZOMBIE_OVERHEATED,new Object[]{ip}},0,ip),TempProgram.getMaliciousIP());
									//send a message to the other player that they are overheated.
									MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT,new Object[]{ip},new Object[]{TempPort.getLastDamageWindowHandle(),TempPort.getAccessing()}},0,ip),TempProgram.getTargetIP());
									MyComputerHandler.addData(new ApplicationData("message",new Object[]{MessageHandler.OVERHEATED_OPPONENT_GAME,new Object[]{ip}},0,ip),TempProgram.getTargetIP());
								}
							}
							if(!sentOverHeatedMessage){
								addMessage(MessageHandler.COMPUTER_OVERHEATED);
								sentOverHeatedMessage = true;
							}
						}
					
						TempPort.setOverHeated(true);//Put the port in an overheated state.
					}
					
					TempPort.checkTimeOut(MyTime.getCurrentTime());//Has the port timed out since it was attacked.
					
					tempCPULoad+=TempPort.getCPUCost();
					tempBaseCPULoad+=TempPort.getBaseCPUCostTotal();
					
					if(TempPort.getOn()==false){//For test.
						TempPort.setAttacking(false);
					}
					
					if((TempPort.getType()==Port.ATTACK||TempPort.getType()==Port.SHIPPING)&&TempPort.getAttacking()){//If a port is attacking force the attack to continue.
						if(TempPort.getProgram() instanceof AttackProgram){
							AttackProgram AP=(AttackProgram)TempPort.getProgram();
							TempPort.addApplicationData(new ApplicationData("attackcontinue",null,AP.getTargetPort(),""),MyTime.getCurrentTime());
						}else{
							ShippingProgram SP=(ShippingProgram)TempPort.getProgram();
							TempPort.addApplicationData(new ApplicationData("attackcontinue",null,SP.getTargetPort(),""),MyTime.getCurrentTime());
						}
					}
					ii++;
				}
				
				baseCPU=tempBaseCPULoad+currentWatchCost;//The base CPU prior to overheating.
				currentCPU=tempCPULoad+currentWatchCost;
				lastAttack=MyTime.getCurrentTime();
				healCounter++;
								
				reportCPU=currentCPU;
				if(overHeated&&currentCPU<=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()){
					reportCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()+1;
				}else if(!overHeated){//Make sure the ports do not think they're overheated.
					PortIterator=Ports.entrySet().iterator();
					while(PortIterator.hasNext()){
						Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
						TempPort.setOverHeated(false);
						sentOverHeatedMessage = false;
					}
				}
									
				if(currentCPU!=startCPU||startReportCPU!=reportCPU)//The CPU Load has Changed.
					healthChange=true;
					
			}else{//Otherwise make sure we still calculate the CPU load.
				currentCPU=0.0f;
				Iterator PortIterator=Ports.entrySet().iterator();
				boolean overHeated=false;
				while(PortIterator.hasNext()){
					Port TempPort=(Port)(((Map.Entry)PortIterator.next()).getValue());
					if(TempPort!=null){
						currentCPU+=TempPort.getCPUCost();
						if(TempPort.getOverHeated()){
							overHeated=true;
						}
					}
				}
				currentCPU+=currentWatchCost;
				
				if(overHeated&&currentCPU<=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus())
					reportCPU=CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus()+1;
				else
					reportCPU=currentCPU;

			}
	}
	
	/**
	This function checks whether or not a packet should currently be sent, be it a damage packet or a standard packet.
	*/
	public void sendStandardPacket(){
		/**
		AT THE END OF THE PACKET TIMEOUT DISPATCH A PACKET TO THE CLIENT.
		*/
		if(systemChange||healthChange)
		if(cpuLoadCalculated&&getLoaded()&&!getLoading()&&MyTime.getCurrentTime()-lastSent>PACKET_TIMEOUT&&connectionID>=0){
			
			if(systemChange){
			
				systemChange=false;
				standardPacketBuilder.populate(PA, buildStandardPacketSnapshot());
				Messages.clear();
				Choices.clear();
				if(LOG_UPDATE){
					LOG_UPDATE=false;
				}
				if(sendPreferences){
					sendPreferences = false;
				}
				
				Object O[]=new Object[]{PA,new Integer(connectionID)};

				MyHackerServer.addData(O);
				lastSent=MyTime.getCurrentTime();
				PA=new PacketAssignment(0);
			}
			
			if(healthChange){
				healthChange=false;
				damagePacketBuilder.populate(DA, buildDamagePacketSnapshot());
				Damage.clear();
						
				Object O[]=new Object[]{DA,new Integer(connectionID)};
				MyHackerServer.addData(O);
				lastSent=MyTime.getCurrentTime();
				DA=new DamageAssignment(0);
			}
		}
	}

	private ComputerStandardPacketSnapshot buildStandardPacketSnapshot(){
		Object[] messageArray = Messages.toArray();
		ArrayList<Object[]> choices = new ArrayList<Object[]>();
		Iterator choiceIterator = Choices.iterator();
		while(choiceIterator.hasNext()){
			choices.add((Object[])choiceIterator.next());
		}

		ArrayList<String[]> logMessages = null;
		if(LOG_UPDATE){
			logMessages = new ArrayList<String[]>();
			Iterator logIterator = LogMessages.iterator();
			while(logIterator.hasNext()){
				logMessages.add((String[])logIterator.next());
			}
		}

		HashMap<String, Object> preferenceCopy = null;
		if(sendPreferences&&preferences!=null){
			preferenceCopy = new HashMap<String, Object>();
			preferenceCopy.putAll(preferences);
		}

		Integer countDownSeconds = null;
		if(countDown){
			countDownSeconds = (int)((COUNTDOWN_LENGTH-(MyTime.getCurrentTime()-countDownStart))/1000);
		}

		return new ComputerStandardPacketSnapshot(
			pettyCash,
			bankMoney,
			CPU_CHART[cputype]+MyEquipmentSheet.getCPUBonus(),
			cputype,
			memorytype,
			defaultBank,
			defaultAttack,
			defaultHTTP,
			defaultFTP,
			defaultShipping,
			successfulHacks,
			voteCount,
			reportCPU,
			MyFileSystem.getHDType(),
			MyFileSystem.getQuantity()-2,
			MyFileSystem.getMaximumSpace(),
			RawComputerHandler.getPlayers(),
			commodityAmount,
			MyEquipmentSheet.getHealBonus(),
			myVotes,
			messageArray,
			choices,
			logMessages,
			countDownSeconds,
			preferenceCopy
		);
	}

	private ComputerDamagePacketSnapshot buildDamagePacketSnapshot(){
		ArrayList<PortHealthSnapshot> healthUpdates = new ArrayList<PortHealthSnapshot>();
		Iterator portIterator = Ports.entrySet().iterator();
		while(portIterator.hasNext()){
			Port tempPort=(Port)(((Map.Entry)portIterator.next()).getValue());
			healthUpdates.add(new PortHealthSnapshot(
				tempPort.getNumber(),
				tempPort.getHealth(),
				tempPort.getCPUCost(),
				tempPort.getFireWall().getType(),
				tempPort.getHealCount(),
				tempPort.getBaseCPUCostAndFirewall(),
				getWindowHandle(tempPort)
			));
		}

		ArrayList<Object[]> damageEntries = new ArrayList<Object[]>();
		Iterator damageIterator = Damage.iterator();
		while(damageIterator.hasNext()){
			damageEntries.add((Object[])damageIterator.next());
		}

		return new ComputerDamagePacketSnapshot(
			(float)(Float)Stats.get("Attack"),
			(float)(Float)Stats.get("Bank"),
			(float)(Float)Stats.get("FireWall"),
			(float)(Float)Stats.get("Watch"),
			(float)(Float)Stats.get("Scanning"),
			(float)(Float)Stats.get("Webdesign"),
			(float)(Float)Stats.get("Redirecting"),
			(float)(Float)Stats.get("Repair"),
			reportCPU,
			healthUpdates,
			damageEntries
		);
	}
	
	/**
	This function takes care of creating a 3D chat packet and dispatching it.
	*/
	public void sendChatPacket(){
		/*HacktendoPacket HP=WorldSingleton.getInstance().getPacket("game",ip);
		if(HP.getSpriteEvents().size()>0){
			Object O[]=new Object[]{HP,new Integer(connectionID)};
			MyHackerServer.addData(O);
		}*/
	}
	
	//Testing main.
	public static void main(String args[]){
	}
	
	
	//
	//
	////////////////// WHAT FOLLOWS IS THE LOADING AND SAVING STEPS EXCLUSIVELY.
	

	/**
	This task loads the save file representing the computer from disk.
	*/	
	private class loadSaveTask implements Task{
		private Computer MyComputer=null;
		private boolean run=false;
		
			public loadSaveTask(Computer MyComputer){
				this.MyComputer=MyComputer;
			}
			public void execute(){
				if(!run){
					run=true;
					
					try{
						if(connectionID!=-1){//is this player allowed to be logged in.
							if(!checkLogin()){
								Object O[]=new Object[]{new LoginFailedAssignment(0),new Integer(connectionID)};
								MyHackerServer.addData(O);
								connectionID=-1;
							}else{
								Object O[]=MyHackerServer.getRandomKey(ip,clientHash,publicKey);
								LoginSuccessAssignment MyLoginSuccessAssignment=new LoginSuccessAssignment(0,ip,(String)O[0],isNPC());
								MyLoginSuccessAssignment.setPublicKey((byte[])O[1]);
								O=new Object[]{MyLoginSuccessAssignment,new Integer(connectionID)};
								MyHackerServer.addData(O);
							}
						}

						boolean activeLoad = !(loadRequester==null||loadRequester.equals(""));
						if(!activeLoad){
							loggedIn = true;
							logInTime = MyTime.getCurrentTime();
						}

						upgradedAccount=false;
						inactive=false;
						MAX_OPS = FREE_MAX_OPS;
						FILE_SIZE_LIMIT = FREE_FILE_SIZE_LIMIT;

						RemoteFunctionPackResult functionPackResult = sessionService.requestFunctionPacks(ip);
						upgradedAccount=functionPackResult.getUpgradedAccount();
						inactive = functionPackResult.getInactive();
						MAX_OPS = functionPackResult.getMaxOps();
						FILE_SIZE_LIMIT = functionPackResult.getFileSizeLimit();

						String xml = sessionService.loadLocalSaveXml(ip,activeLoad);
						ComputerSnapshot snapshot = xmlComputerPersistence.parse(xml);
						persistenceSupport.restoreSnapshot(MyComputer,snapshot);
					}catch(Exception e){
						String message = e.getMessage();
						if(message==null||message.equals("")){
							message = "Unable to load local account data for ip=" + ip + ".";
						}
						errorMessage = message;
					e.printStackTrace();
					LOAD_FAILURE=true;
				}		
				Loaded=true;
				Loading=false;
			
						
			//Add the player to the network and get the network information.
			if(type!=NPC)
				try{
					RawComputerHandler.incrementPlayers();
					Network.getInstance(MyComputerHandler).addToNetwork(Network.ROOT_NETWORK,ip);
					PacketNetwork PN=Network.getInstance(MyComputerHandler).getNetworkInformation(Network.ROOT_NETWORK);
					store=PN.getStoreIP();
					PA.setPacketNetwork(PN);
				}catch(Exception e){
					e.printStackTrace();
				}
			}

			//WorldSingleton.getInstance().addPlayer("game",ip,userName,npc);//Add the player into the 3D chat.
			
			systemChange=true;
			healthChange=true;
            sendPreferences = true;
			MyEquipmentSheet.degradeEquipment();//Make sure that equipment starts out degraded.
			MyComputerHandler.addData(new ApplicationData("requestequipment",new Integer(13),0,ip),ip);
		}
	}
	
	
	
	
	/**
	Output the contents of this class as an XML string.
	*/
	public String outputXML() throws Exception{
		String returnMe="";
		try{
			returnMe = persistenceSupport.outputXml(this);
		}catch(Exception e){
			try{
				BufferedWriter Out=new BufferedWriter(new FileWriter("saveerror.txt",true));
				Out.write(returnMe);
				Out.write(e.toString());
				Out.close();
			}catch(Exception e2){
				e.printStackTrace();
			}
			throw(e);
		}
		return(returnMe);
	}
    
    /**
	This function encapsulates the loading of the file data-structure.
	*/
    public HackerFile loadFile(Node N,LoadXML LX){
        return persistenceSupport.loadFile(N,LX);      
    }
}
